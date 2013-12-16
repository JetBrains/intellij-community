/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.server;

import com.intellij.compiler.make.CachingSearcher;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.cls.ClsUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/18/12
 */
public abstract class DefaultMessageHandler implements BuilderMessageHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.DefaultMessageHandler");
  private final int MAX_CONSTANT_SEARCHES = Registry.intValue("compiler.max.static.constants.searches");
  private final Project myProject;
  private int myConstantSearchesCount = 0;
  private final CachingSearcher mySearcher;
  private final SequentialTaskExecutor myTaskExecutor = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);

  protected DefaultMessageHandler(Project project) {
    myProject = project;
    mySearcher = new CachingSearcher(project);
  }

  @Override
  public void buildStarted(UUID sessionId) {
  }

  @Override
  public final void handleBuildMessage(final Channel channel, final UUID sessionId, final CmdlineRemoteProto.Message.BuilderMessage msg) {
    switch (msg.getType()) {
      case BUILD_EVENT:
        handleBuildEvent(sessionId, msg.getBuildEvent());
        break;
      case COMPILE_MESSAGE:
        handleCompileMessage(sessionId, msg.getCompileMessage());
        break;
      case CONSTANT_SEARCH_TASK:
        final CmdlineRemoteProto.Message.BuilderMessage.ConstantSearchTask task = msg.getConstantSearchTask();
        myTaskExecutor.submit(new Runnable() {
          @Override
          public void run() {
            handleConstantSearchTask(channel, sessionId, task);
          }
        });
        break;
    }
  }

  protected abstract void handleCompileMessage(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message);

  protected abstract void handleBuildEvent(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event);

  private void handleConstantSearchTask(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.ConstantSearchTask task) {
    final String ownerClassName = task.getOwnerClassName();
    final String fieldName = task.getFieldName();
    final int accessFlags = task.getAccessFlags();
    final boolean accessChanged = task.getIsAccessChanged();
    final boolean isRemoved = task.getIsFieldRemoved();
    final Ref<Boolean> isSuccess = Ref.create(Boolean.TRUE);
    final Set<String> affectedPaths = Collections.synchronizedSet(new HashSet<String>()); // PsiSearchHelper runs multiple threads
    try {
      if (isDumbMode()) {
        // do not wait until dumb mode finishes
        isSuccess.set(Boolean.FALSE);
        LOG.debug("Constant search task: cannot search in dumb mode");
      }
      else {
        final String qualifiedName = ownerClassName.replace('$', '.');
        final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
          public PsiClass[] compute() {
            return JavaPsiFacade.getInstance(myProject).findClasses(qualifiedName, GlobalSearchScope.allScope(myProject));
          }
        });

        try {
          if (isRemoved) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                if (classes.length > 0) {
                  for (PsiClass aClass : classes) {
                    final boolean success = aClass.isValid() && performRemovedConstantSearch(aClass, fieldName, accessFlags, affectedPaths);
                    if (!success) {
                      isSuccess.set(Boolean.FALSE);
                      break;
                    }
                  }
                }
                else {
                  isSuccess.set(
                    performRemovedConstantSearch(null, fieldName, accessFlags, affectedPaths)
                  );
                }
              }
            });
          }
          else {
            if (classes.length > 0) {
              final Collection<PsiField> changedFields = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiField>>() {
                public Collection<PsiField> compute() {
                  final List<PsiField> fields = new SmartList<PsiField>();
                  for (PsiClass aClass : classes) {
                    if (!aClass.isValid()) {
                      return Collections.emptyList();
                    }
                    final PsiField changedField = aClass.findFieldByName(fieldName, false);
                    if (changedField != null) {
                      fields.add(changedField);
                    }
                  }
                  return fields;
                }
              });
              if (changedFields.isEmpty()) {
                isSuccess.set(Boolean.FALSE);
                LOG.debug("Constant search task: field " + fieldName + " not found in classes " + qualifiedName);
              }
              else {
                for (final PsiField changedField : changedFields) {
                  final boolean success = performChangedConstantSearch(changedField, accessFlags, accessChanged, affectedPaths);
                  if (!success) {
                    isSuccess.set(Boolean.FALSE);
                    break;
                  }
                }
              }
            }
            else {
              isSuccess.set(Boolean.FALSE);
              LOG.debug("Constant search task: class " + qualifiedName + " not found");
            }
          }
        }
        catch (Throwable e) {
          isSuccess.set(Boolean.FALSE);
          LOG.debug("Constant search task: failed with message " + e.getMessage());
        }
      }
    }
    finally {
      final CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult.Builder builder = CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult.newBuilder();
      builder.setOwnerClassName(ownerClassName);
      builder.setFieldName(fieldName);
      if (isSuccess.get()) {
        builder.setIsSuccess(true);
        builder.addAllPath(affectedPaths);
        LOG.debug("Constant search task: " + affectedPaths.size() + " affected files found");
      }
      else {
        builder.setIsSuccess(false);
        LOG.debug("Constant search task: unsuccessful");
      }
      channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(
        CmdlineRemoteProto.Message.ControllerMessage.Type.CONSTANT_SEARCH_RESULT).setConstantSearchResult(builder.build()).build()
      ));
    }
  }

  private boolean isDumbMode() {
    final DumbService dumbService = DumbService.getInstance(myProject);
    boolean isDumb = dumbService.isDumb();
    if (isDumb) {
      // wait some time
      for (int idx = 0; idx < 5; idx++) {
        try {
          Thread.sleep(10L);
        }
        catch (InterruptedException ignored) {
        }
        isDumb = dumbService.isDumb();
        if (!isDumb) {
          break;
        }
      }
    }
    return isDumb;
  }

  private boolean performChangedConstantSearch(PsiField field, int accessFlags, boolean isAccessibilityChange, final Set<String> affectedPaths) {
    if (!isAccessibilityChange && ClsUtil.isPrivate(accessFlags)) {
      return true; // optimization: don't need to search, cause may be used only in this class
    }
    final Set<PsiElement> usages = new HashSet<PsiElement>();
    try {
      addUsages(field, usages, accessFlags, isAccessibilityChange);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (final PsiElement usage : usages) {
            if (usage.isValid()) {
              // if usage is invalid the file should be changed anyway and thus compiled later
              affect(usage, affectedPaths);
            }
          }
        }
      });
    }
    catch (PsiInvalidElementAccessException ignored) {
      LOG.debug("Constant search task: PIEAE thrown while searching of usages of changed constant");
      return false;
    }
    catch (ProcessCanceledException ignored) {
      LOG.debug("Constant search task: PCE thrown while searching of usages of changed constant");
      return false;
    }
    return true;
  }


  private boolean performRemovedConstantSearch(@Nullable final PsiClass aClass, String fieldName, int fieldAccessFlags, final Set<String> affectedPaths) {
    final PsiSearchHelper psiSearchHelper = PsiSearchHelper.SERVICE.getInstance(myProject);

    final Ref<Boolean> result = new Ref<Boolean>(Boolean.TRUE);
    processIdentifiers(psiSearchHelper, new PsiElementProcessor<PsiIdentifier>() {
      @Override
      public boolean execute(@NotNull PsiIdentifier identifier) {
        try {
          final PsiElement parent = identifier.getParent();
          if (parent instanceof PsiReferenceExpression) {
            final PsiClass ownerClass = getOwnerClass(parent);
            if (ownerClass != null /*&& !ownerClass.equals(aClass)*/) {
              if (ownerClass.getQualifiedName() != null) {
                affect(ownerClass, affectedPaths);
              }
            }
          }
          return true;
        }
        catch (PsiInvalidElementAccessException ignored) {
          result.set(Boolean.FALSE);
          LOG.debug("Constant search task: PIEAE thrown while searching of usages of removed constant");
          return false;
        }
      }
    }, fieldName, getSearchScope(aClass, fieldAccessFlags), UsageSearchContext.IN_CODE);

    return result.get();
  }

  private SearchScope getSearchScope(PsiClass aClass, int fieldAccessFlags) {
    SearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    if (aClass != null && ClsUtil.isPackageLocal(fieldAccessFlags)) {
      final PsiFile containingFile = aClass.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        final String packageName = ((PsiJavaFile)containingFile).getPackageName();
        final PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
        if (aPackage != null) {
          searchScope = PackageScope.packageScope(aPackage, false);
          searchScope = searchScope.intersectWith(aClass.getUseScope());
        }
      }
    }
    return searchScope;
  }

  private static void affect(PsiElement ownerClass, Set<String> affectedPaths) {
    final PsiFile containingPsi = ownerClass.getContainingFile();
    if (containingPsi != null) {
      final VirtualFile vFile = containingPsi.getOriginalFile().getVirtualFile();
      if (vFile != null) {
        affectedPaths.add(vFile.getPath());
      }
    }
  }

  private static boolean processIdentifiers(PsiSearchHelper helper, @NotNull final PsiElementProcessor<PsiIdentifier> processor, @NotNull final String identifier, @NotNull SearchScope searchScope, short searchContext) {
    TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      @Override
      public boolean execute(PsiElement element, int offsetInElement) {
        return !(element instanceof PsiIdentifier) || processor.execute((PsiIdentifier)element);
      }
    };
    return helper.processElementsWithWord(processor1, searchScope, identifier, searchContext, true, false);
  }

  private void addUsages(final PsiField psiField, final Collection<PsiElement> usages, final int fieldAccessFlags, final boolean ignoreAccessScope) throws ProcessCanceledException {
    final Queue<PsiField> fieldsToProcess = new ArrayDeque<PsiField>();
    fieldsToProcess.add(psiField);
    for (PsiField field = fieldsToProcess.poll(); field != null; field = fieldsToProcess.poll()) {
      final int count = myConstantSearchesCount;
      if (count > MAX_CONSTANT_SEARCHES) {
        throw new ProcessCanceledException();
      }
      final PsiField fieldToCheck = field;
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (!fieldToCheck.isValid()) {
            // if field is invalid, the file might be changed, so next time it is compiled,
            // the constant value change, if any, will be processed
            return;
          }
          final Collection<PsiReferenceExpression> references = doFindReferences(fieldToCheck, fieldAccessFlags, ignoreAccessScope);

          myConstantSearchesCount++;

          for (final PsiReferenceExpression ref : references) {
            PsiElement e = ref.getElement();
            usages.add(e);
            PsiField ownerField = getOwnerField(e);
            if (ownerField != null && ownerField.hasModifierProperty(PsiModifier.FINAL)) {
              final PsiExpression initializer = ownerField.getInitializer();
              if (initializer != null && PsiUtil.isConstantExpression(initializer)) {
                // if the field depends on the compile-time-constant expression and is itself final
                fieldsToProcess.add(ownerField);
              }
            }
          }
        }
      });
    }
  }

  private Collection<PsiReferenceExpression> doFindReferences(final PsiField psiField, int fieldAccessFlags, boolean ignoreAccessScope) {
    final Collection<PsiReferenceExpression> result = Collections.synchronizedList(new SmartList<PsiReferenceExpression>());

    final SearchScope searchScope = ignoreAccessScope? GlobalSearchScope.projectScope(myProject) : getSearchScope(psiField.getContainingClass(), fieldAccessFlags);

    processIdentifiers(PsiSearchHelper.SERVICE.getInstance(myProject), new PsiElementProcessor<PsiIdentifier>() {
      @Override
      public boolean execute(@NotNull PsiIdentifier identifier) {
        final PsiElement parent = identifier.getParent();
        if (parent instanceof PsiReferenceExpression) {
          final PsiReferenceExpression refExpression = (PsiReferenceExpression)parent;
          if (refExpression.isReferenceTo(psiField)) {
            result.add(refExpression);
          }
        }
        return true;
      }
    }, psiField.getName(), searchScope, UsageSearchContext.IN_CODE);

    return result;
  }

  @Nullable
  private static PsiClass getOwnerClass(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile) { // top-level class
        final PsiClass psiClass = (PsiClass)element;
        if (JspPsiUtil.isInJspFile(psiClass)) {
          return null;
        }
        final PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
          return null;
        }
        return JavaLanguage.INSTANCE.equals(containingFile.getLanguage())? psiClass : null;
      }
      element = element.getParent();
    }
    return null;
  }

  @Nullable
  private static PsiField getOwnerField(PsiElement element) {
    while (!(element instanceof PsiFile)) {
      if (element instanceof PsiClass) {
        break;
      }
      if (element instanceof PsiField) { // top-level class
        return (PsiField)element;
      }
      element = element.getParent();
    }
    return null;
  }

}

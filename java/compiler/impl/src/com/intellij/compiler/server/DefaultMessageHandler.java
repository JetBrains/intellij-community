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
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.cls.ClsUtil;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.SequentialTaskExecutor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/18/12
 */
public abstract class DefaultMessageHandler implements BuilderMessageHandler {
  private final int MAX_CONSTANT_SEARCHES = Registry.intValue("compiler.max.static.constants.searches");
  private final Project myProject;
  private int myConstantSearchesCount = 0;
  private final CachingSearcher mySearcher;
  private final SequentialTaskExecutor myTaskExecutor = new SequentialTaskExecutor(new Executor() {
    @Override
    public void execute(Runnable command) {
      ApplicationManager.getApplication().executeOnPooledThread(command);
    }
  });

  protected DefaultMessageHandler(Project project) {
    myProject = project;
    mySearcher = new CachingSearcher(project);
  }

  @Override
  public final void handleBuildMessage(final Channel channel, final UUID sessionId, final CmdlineRemoteProto.Message.BuilderMessage msg) {
    switch (msg.getType()) {
      case BUILD_EVENT:
        handleBuildEvent(msg.getBuildEvent());
        break;
      case COMPILE_MESSAGE:
        handleCompileMessage(msg.getCompileMessage());
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

  protected abstract void handleCompileMessage(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message);

  protected abstract void handleBuildEvent(CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event);

  private void handleConstantSearchTask(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.ConstantSearchTask task) {
    final String ownerClassName = task.getOwnerClassName();
    final String fieldName = task.getFieldName();
    final int accessFlags = task.getAccessFlags();
    final boolean accessChanged = task.getIsAccessChanged();
    final boolean isRemoved = task.getIsFieldRemoved();
    final Ref<Boolean> isSuccess = Ref.create(Boolean.TRUE);
    final Set<String> affectedPaths = new HashSet<String>();
    try {
      if (isDumbMode()) {
        // do not wait until dumb mode finishes
        isSuccess.set(Boolean.FALSE);
      }
      else {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            try {
              final PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(ownerClassName.replace('$', '.'), GlobalSearchScope.allScope(myProject));
              if (isRemoved) {
                if (classes.length > 0) {
                  for (PsiClass aClass : classes) {
                    final boolean sucess = performRemovedConstantSearch(aClass, fieldName, accessFlags, affectedPaths);
                    if (!sucess) {
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
              else {
                if (classes.length > 0) {
                  boolean foundAtLeastOne = false;
                  for (PsiClass aClass : classes) {
                    PsiField changedField = null;
                    for (PsiField psiField : aClass.getFields()) {
                      if (fieldName.equals(psiField.getName())) {
                        changedField = psiField;
                        break;
                      }
                    }
                    if (changedField == null) {
                      continue;
                    }
                    foundAtLeastOne = true;
                    final boolean sucess = performChangedConstantSearch(aClass, changedField, accessFlags, accessChanged, affectedPaths);
                    if (!sucess) {
                      isSuccess.set(Boolean.FALSE);
                      break;
                    }
                  }
                  if (!foundAtLeastOne) {
                    isSuccess.set(Boolean.FALSE);
                  }
                }
                else {
                  isSuccess.set(Boolean.FALSE);
                }
              }
            }
            catch (Throwable e) {
              isSuccess.set(Boolean.FALSE);
            }
          }
        });
      }
    }
    finally {
      final CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult.Builder builder = CmdlineRemoteProto.Message.ControllerMessage.ConstantSearchResult.newBuilder();
      builder.setOwnerClassName(ownerClassName);
      builder.setFieldName(fieldName);
      if (isSuccess.get()) {
        builder.setIsSuccess(true);
        builder.addAllPath(affectedPaths);
      }
      else {
        builder.setIsSuccess(false);
      }
      Channels.write(channel, CmdlineProtoUtil.toMessage(sessionId, CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(
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

  private boolean performChangedConstantSearch(PsiClass aClass, PsiField field, int accessFlags, boolean isAccessibilityChange, Set<String> affectedPaths) {
    if (!isAccessibilityChange && ClsUtil.isPrivate(accessFlags)) {
      return true; // optimization: don't need to search, cause may be used only in this class
    }
    final Set<PsiElement> usages = new HashSet<PsiElement>();
    try {
      addUsages(field, usages, isAccessibilityChange);
      for (final PsiElement usage : usages) {
        affect(usage, affectedPaths);
        //final PsiClass ownerClass = getOwnerClass(usage);
        //if (ownerClass != null && !ownerClass.equals(aClass)) {
        //  affect(ownerClass, affectedPaths);
        //}
        //else if (ownerClass == null) {
        //  affect(usage, affectedPaths);
        //}
      }
    }
    catch (PsiInvalidElementAccessException e) {
      return false;
    }
    catch (ProcessCanceledException e) {
      return false;
    }
    return true;
  }


  private boolean performRemovedConstantSearch(@Nullable final PsiClass aClass, String fieldName, int accessFlags, final Set<String> affectedPaths) {
    SearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    if (aClass != null && ClsUtil.isPackageLocal(accessFlags)) {
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
        catch (PsiInvalidElementAccessException e) {
          result.set(Boolean.FALSE);
          return false;
        }
      }
    }, fieldName, searchScope, UsageSearchContext.IN_CODE);

    return result.get();
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
      public boolean execute(PsiElement element, int offsetInElement) {
        return !(element instanceof PsiIdentifier) || processor.execute((PsiIdentifier)element);
      }
    };
    return helper.processElementsWithWord(processor1, searchScope, identifier, searchContext, true);
  }

  private void addUsages(PsiField psiField, Collection<PsiElement> usages, final boolean ignoreAccessScope) throws ProcessCanceledException {
    final int count = myConstantSearchesCount;
    if (count > MAX_CONSTANT_SEARCHES) {
      throw new ProcessCanceledException();
    }
    Collection<PsiReference> references = mySearcher.findReferences(psiField, ignoreAccessScope)/*doFindReferences(searchHelper, psiField)*/;

    myConstantSearchesCount++;

    for (final PsiReference ref : references) {
      if (!(ref instanceof PsiReferenceExpression)) {
        continue;
      }
      PsiElement e = ref.getElement();
      usages.add(e);
      PsiField ownerField = getOwnerField(e);
      if (ownerField != null) {
        if (ownerField.hasModifierProperty(PsiModifier.FINAL)) {
          PsiExpression initializer = ownerField.getInitializer();
          if (initializer != null && PsiUtil.isConstantExpression(initializer)) {
            // if the field depends on the compile-time-constant expression and is itself final
            addUsages(ownerField, usages, ignoreAccessScope);
          }
        }
      }
    }
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
        return StdLanguages.JAVA.equals(containingFile.getLanguage())? psiClass : null;
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

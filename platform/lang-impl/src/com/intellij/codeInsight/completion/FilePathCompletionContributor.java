/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author spleaner
 */
public class FilePathCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.FilePathCompletionContributor");

  public FilePathCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiReference psiReference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
        if (getReference(psiReference) != null) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
          final CompletionService service = CompletionService.getCompletionService();
          if (/*StringUtil.isEmpty(service.getAdvertisementText()) &&*/ shortcut != null) {
            service.setAdvertisementText(CodeInsightBundle.message("class.completion.file.path", shortcut));
          }
        }
      }
    });

    extend(CompletionType.CLASS_NAME, psiElement(), new CompletionProvider<CompletionParameters>(false) {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet _result) {
        @NotNull final CompletionResultSet result = _result.caseInsensitive();
        final PsiElement e = parameters.getPosition();
        final Project project = e.getProject();

        final PsiReference psiReference = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
          public PsiReference compute() {
            //noinspection ConstantConditions
            return parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
          }
        });

        final Pair<FileReference, Boolean> fileReferencePair = getReference(psiReference);
        if (fileReferencePair != null) {
          final FileReference first = fileReferencePair.getFirst();
          if (first == null) return;

          final FileReferenceSet set = first.getFileReferenceSet();
          String prefix = set.getPathString().substring(0, parameters.getOffset() - set.getElement().getTextRange().getStartOffset() - set.getStartInElement());
          
          final List<String>[] pathPrefixParts = new List[] {null};
          int lastSlashIndex;
          if ((lastSlashIndex = prefix.lastIndexOf('/')) != -1) {
            pathPrefixParts[0] = StringUtil.split(prefix.substring(0, lastSlashIndex), "/");
            prefix = prefix.substring(lastSlashIndex + 1);
          }

          final CompletionResultSet __result = result.withPrefixMatcher(prefix).caseInsensitive();

          final PsiFile originalFile = parameters.getOriginalFile();
          final VirtualFile contextFile = originalFile.getVirtualFile();
          if (contextFile != null) {
            final String[] fileNames = getAllNames(project);
            final Set<String> resultNames = new TreeSet<String>();
            for (String fileName : fileNames) {
              if (filenameMatchesPrefixOrType(fileName, prefix, set.getSuitableFileTypes(), parameters.getInvocationCount())) {
                resultNames.add(fileName);
              }
            }

            final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

            final Module contextModule = index.getModuleForFile(contextFile);
            if (contextModule != null) {
              final FileReferenceHelper contextHelper = FileReferenceHelperRegistrar.getNotNullHelper(originalFile);

              final GlobalSearchScope scope = ProjectScope.getProjectScope(project);
              for (final String name : resultNames) {
                ProgressManager.checkCanceled();

                final PsiFile[] files = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
                  public PsiFile[] compute() {
                    return FilenameIndex.getFilesByName(project, name, scope);
                  }
                });

                if (files.length > 0) {
                  for (final PsiFile file : files) {
                    ProgressManager.checkCanceled();

                    ApplicationManager.getApplication().runReadAction(new Runnable() {
                      public void run() {
                        final VirtualFile virtualFile = file.getVirtualFile();
                        if (virtualFile != null && virtualFile.isValid() && virtualFile != contextFile) {
                          if (contextHelper.isMine(project, virtualFile)) {
                            if (pathPrefixParts[0] == null || fileMatchesPathPrefix(contextHelper.getPsiFileSystemItem(project, virtualFile), pathPrefixParts[0])) {
                              __result.addElement(new FilePathLookupItem(file, contextHelper));
                            }
                          }
                        }
                      }
                    });
                  }
                }
              }
            }
          }

          if (set.getSuitableFileTypes().length > 0 && parameters.getInvocationCount() == 1) {
            final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
            final CompletionService service = CompletionService.getCompletionService();
            if (shortcut != null) {
              service.setAdvertisementText(CodeInsightBundle.message("class.completion.file.path.all.variants", shortcut));
            }
          }

          if (fileReferencePair.getSecond()) result.stopHere();
        }
      }
    });
  }

  private static boolean filenameMatchesPrefixOrType(final String fileName, final String prefix, final FileType[] suitableFileTypes, final int invocationCount) {
    final boolean prefixMatched = prefix.length() == 0 || StringUtil.startsWithIgnoreCase(fileName, prefix);
    if (prefixMatched && (suitableFileTypes.length == 0 || invocationCount > 1)) return true;

    if (prefixMatched) {
      final String extension = FileUtil.getExtension(fileName);
      if (extension.length() == 0) return false;

      for (final FileType fileType : suitableFileTypes) {
        final List<FileNameMatcher> matchers = FileTypeManager.getInstance().getAssociations(fileType);
        for (final FileNameMatcher matcher : matchers) {
          if (matcher.accept(fileName)) return true;
        }
      }
    }

    return false;
  }

  private static boolean fileMatchesPathPrefix(@Nullable final PsiFileSystemItem file, @NotNull final List<String> pathPrefix) {
    if (file == null) return false;

    final List<String> contextParts = new ArrayList<String>();
    PsiFileSystemItem parentFile = file;
    PsiFileSystemItem parent;
    while ((parent = parentFile.getParent()) != null) {
      if (parent.getName().length() > 0) contextParts.add(0, parent.getName().toLowerCase());
      parentFile = parent;
    }

    final String path = StringUtil.join(contextParts, "/");

    int nextIndex = 0;
    for (final String s : pathPrefix) {
      if ((nextIndex = path.indexOf(s.toLowerCase(), nextIndex)) == -1) return false;
    }

    return true;
  }

  private static String[] getAllNames(@NotNull final Project project) {
    Set<String> names = new HashSet<String>();
    final ChooseByNameContributor[] nameContributors = ChooseByNameContributor.FILE_EP_NAME.getExtensions();
    for (final ChooseByNameContributor contributor : nameContributors) {
      try {
        names.addAll(ApplicationManager.getApplication().runReadAction(new Computable<Collection<? extends String>>() {
          public Collection<? extends String> compute() {
            return Arrays.asList(contributor.getNames(project, false));
          }
        }));
      }
      catch (ProcessCanceledException ex) {
        // index corruption detected, ignore
      }
      catch (Exception ex) {
        LOG.error(ex);
      }
    }

    return ArrayUtil.toStringArray(names);
  }

  @Nullable
  private static Pair<FileReference, Boolean> getReference(final PsiReference original) {
    if (original == null) {
      return null;
    }

    if (original instanceof PsiMultiReference) {
      final PsiMultiReference multiReference = (PsiMultiReference)original;
      for (PsiReference reference : multiReference.getReferences()) {
        if (reference instanceof FileReference) {
          return Pair.create((FileReference) reference, false);
        }
      }
    }
    else if (original instanceof FileReferenceOwner) {
      final FileReference fileReference = ((FileReferenceOwner)original).getLastFileReference();
      if (fileReference != null) {
        return Pair.create(fileReference, true);
      }
    }

    return null;
  }

  public class FilePathLookupItem extends LookupElement {
    private final String myName;
    private final String myPath;
    private final String myInfo;
    private final Icon myIcon;
    private final PsiFile myFile;
    private final FileReferenceHelper myReferenceHelper;

    public FilePathLookupItem(@NotNull final PsiFile file, @NotNull final FileReferenceHelper referenceHelper) {
      myName = file.getName();
      myPath = file.getVirtualFile().getPath();

      myReferenceHelper = referenceHelper;

      myInfo = FileInfoManager.getFileAdditionalInfo(file);
      myIcon = file.getFileType().getIcon();

      myFile = file;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    @Override
    public String toString() {
      return String.format("%s%s", myName, myInfo == null ? "" : " (" + myInfo + ")");
    }

    @NotNull
    @Override
    public Object getObject() {
      return myFile;
    }                                                                   

    @NotNull
    public String getLookupString() {
      return myName;
    }

    @Override
    public void handleInsert(InsertionContext context) {
      if (myFile.isValid()) {
        final PsiReference psiReference = context.getFile().findReferenceAt(context.getStartOffset());
        final Pair<FileReference, Boolean> fileReferencePair = getReference(psiReference);
        LOG.assertTrue(fileReferencePair != null);

        fileReferencePair.getFirst().bindToElement(myFile, true);
      }
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      final VirtualFile virtualFile = myFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      final PsiFileSystemItem root = myReferenceHelper.findRoot(myFile.getProject(), virtualFile);
      final String relativePath = PsiFileSystemItemUtil.getRelativePath(root, myReferenceHelper.getPsiFileSystemItem(myFile.getProject(), virtualFile));

      final StringBuilder sb = new StringBuilder();
      if (myInfo != null) {
        sb.append(" (").append(myInfo);
      }

      if (relativePath != null && !relativePath.equals(myName)) {
        if (myInfo != null) {
          sb.append(", ");
        }
        else {
          sb.append(" (");
        }

        sb.append(relativePath);
      }

      if (sb.length() > 0) {
        sb.append(')');
      }

      presentation.setItemText(myName);

      if (sb.length() > 0) {
        presentation.setTailText(sb.toString(), true);
      }

      presentation.setIcon(myIcon);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FilePathLookupItem that = (FilePathLookupItem)o;

      if (!myName.equals(that.myName)) return false;
      if (!myPath.equals(that.myPath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + myPath.hashCode();
      return result;
    }
  }
}

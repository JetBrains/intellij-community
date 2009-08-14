package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileInfoManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceOwner;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileSystemItemUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

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
        final String prefix = result.getPrefixMatcher().getPrefix();
        if (prefix.length() == 0) {
          return;
        }

        final PsiReference psiReference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
        if (getReference(psiReference) != null) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
          final CompletionService service = CompletionService.getCompletionService();
          if (StringUtil.isEmpty(service.getAdvertisementText()) && shortcut != null) {
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
        final String prefix = _result.getPrefixMatcher().getPrefix();
        if (prefix.length() == 0) { // todo[spL]: no prefix no completion
          return;
        }

        @NotNull final CompletionResultSet result = _result.caseInsensitive();

        final PsiElement e = parameters.getPosition();


        final PsiReference psiReference = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
          public PsiReference compute() {
            //noinspection ConstantConditions
            return parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
          }
        });

        if (getReference(psiReference) != null) {
          final Project project = e.getProject();
          final String[] fileNames = getAllNames(project);
          final List<String> resultNames = new ArrayList<String>();
          for (String fileName : fileNames) {
            if (StringUtil.startsWithIgnoreCase(fileName, prefix)) {
              resultNames.add(fileName);
            }
          }

          final LogicalRootsManager logicalRootsManager = LogicalRootsManager.getLogicalRootsManager(project);
          final PsiManager psiManager = PsiManager.getInstance(project);

          final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();

          final VirtualFile contextFile = parameters.getOriginalFile().getVirtualFile();
          if (contextFile != null) {
            final Module contextModule = index.getModuleForFile(contextFile);
            if (contextModule != null) {
              final Module[] dependencies = ModuleRootManager.getInstance(contextModule).getDependencies();

              final Set<Module> modules = new HashSet<Module>(dependencies.length + 1);
              modules.addAll(Arrays.asList(dependencies));
              modules.add(contextModule);

              final LogicalRoot contextRoot = logicalRootsManager.findLogicalRoot(contextFile);
              if (contextRoot != null) {
                final VirtualFile contextRootFile = contextRoot.getVirtualFile();
                final LogicalRootType contextRootType = contextRoot.getType();

                final GlobalSearchScope scope = ProjectScope.getProjectScope(project);
                for (final String name : resultNames) {
                  ProgressManager.getInstance().checkCanceled();

                  final PsiFile[] files = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
                    public PsiFile[] compute() {
                      return FilenameIndex.getFilesByName(project, name, scope);
                    }
                  });

                  if (files.length > 0) {
                    for (final PsiFile file : files) {
                      ApplicationManager.getApplication().runReadAction(new Runnable() {
                        public void run() {
                          final VirtualFile virtualFile = file.getVirtualFile();
                          if (virtualFile != null && virtualFile.isValid()) {
                            final Module module = index.getModuleForFile(virtualFile);
                            if (modules.contains(module)) {
                              final LogicalRoot logicalRoot = logicalRootsManager.findLogicalRoot(virtualFile);
                              if (logicalRoot != null && contextRootType == logicalRoot.getType()) {
                                final VirtualFile _context = contextRoot == logicalRoot ? contextRootFile : project.getBaseDir();
                                if (_context != null) {
                                  final PsiDirectory psiFile = psiManager.findDirectory(_context);
                                  if (psiFile != null) {
                                    result.addElement(new FilePathLookupItem(file, psiFile));
                                  }
                                }
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
          }
        }
      }
    });
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
  private static PsiReference getReference(final PsiReference original) {
    if (original == null) {
      return null;
    }

    if (original instanceof PsiMultiReference) {
      final PsiMultiReference multiReference = (PsiMultiReference)original;
      for (PsiReference reference : multiReference.getReferences()) {
        if (reference instanceof FileReference) {
          return reference;
        }
      }
    }
    else if (original instanceof FileReferenceOwner) {
      final FileReference fileReference = ((FileReferenceOwner)original).getLastFileReference();
      if (fileReference != null) {
        return fileReference;
      }
    }

    return null;
  }

  public class FilePathLookupItem extends LookupElement {
    private final String myRelativePath;
    private final String myName;
    private final String myPath;
    private final String myInfo;
    private final Icon myIcon;
    private final PsiFile myFile;

    public FilePathLookupItem(@NotNull final PsiFile file, @NotNull final PsiFileSystemItem context) {
      myName = file.getName();
      myPath = file.getVirtualFile().getPath();

      final String relative = PsiFileSystemItemUtil.getRelativePath(context, file);

      myRelativePath = relative == null ? null : relative.equals(myName) ? null : relative;

      myInfo = FileInfoManager.getFileAdditionalInfo(file);
      myIcon = file.getFileType().getIcon();

      myFile = file;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    @Override
    public String toString() {
      return String.format("%s (%s, %s)", myName, myInfo == null ? "" : myInfo, myRelativePath == null ? "" : myRelativePath);
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
        final PsiReference fileReference = getReference(psiReference);
        LOG.assertTrue(fileReference != null);

        fileReference.bindToElement(myFile);
      }
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      final StringBuilder sb = new StringBuilder();
      if (myInfo != null) {
        sb.append(" (").append(myInfo);
      }

      if (myRelativePath != null) {
        if (myInfo != null) {
          sb.append(", ");
        }
        else {
          sb.append(" (");
        }

        sb.append(myRelativePath);
      }

      if (sb.length() > 0) {
        sb.append(')');
      }

      presentation.setItemText(myName);

      if (sb.length() > 0) {
        presentation.setTailText(sb.toString(), true, false, false);
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

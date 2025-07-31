// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaSuperTypeSearchUtil;
import com.intellij.codeInsight.javadoc.SnippetMarkup;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.platform.backend.navigation.NavigationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.MemberOrderService;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.javadoc.PsiSnippetAttributeValueImpl;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;


@ApiStatus.NonExtendable
public class JavaPsiImplementationHelperImpl extends JavaPsiImplementationHelper {
  private static final Logger LOG = Logger.getInstance(JavaPsiImplementationHelperImpl.class);

  private final Project myProject;

  public JavaPsiImplementationHelperImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull PsiClass getOriginalClass(@NotNull PsiClass psiClass) {
    return findCompiledElement(myProject, psiClass, scope -> {
      String fqn = psiClass.getQualifiedName();
      return fqn != null ? Arrays.asList(JavaPsiFacade.getInstance(myProject).findClasses(fqn, scope)) : Collections.emptyList();
    });
  }

  @Override
  public @NotNull PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module) {
    return findCompiledElement(myProject, module, scope -> JavaPsiFacade.getInstance(myProject).findModules(module.getName(), scope));
  }

  public static <T extends PsiElement> T findCompiledElement(Project project, T original, Function<? super GlobalSearchScope, ? extends Collection<T>> candidateFinder) {
    PsiCompiledElement cls = original.getUserData(ClsElementImpl.COMPILED_ELEMENT);
    if (cls != null && cls.isValid()) {
      @SuppressWarnings("unchecked") T t = (T)cls;
      return t;
    }

    if (!DumbService.isDumb(project)) {
      VirtualFile vFile = original.getContainingFile().getVirtualFile();
      ProjectFileIndex idx = ProjectRootManager.getInstance(project).getFileIndex();
      if (vFile != null && idx.isInLibrarySource(vFile)) {
        GlobalSearchScope librariesScope = LibraryScopeCache.getInstance(project).getLibrariesOnlyScope();
        Set<OrderEntry> originalEntries = new HashSet<>(idx.getOrderEntriesForFile(vFile));
        for (T candidate : candidateFinder.apply(librariesScope)) {
          PsiFile candidateFile = candidate.getContainingFile();
          if (candidateFile != null) {
            VirtualFile candidateVFile = candidateFile.getVirtualFile();
            if (candidateVFile != null) {
              for (OrderEntry candidateEntry : idx.getOrderEntriesForFile(candidateVFile)) {
                if (originalEntries.contains(candidateEntry)) return candidate;
              }
            }
          }
        }
      }
    }

    return original;
  }

  @Override
  public @NotNull PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile) {
    Function<VirtualFile, VirtualFile> finder = null;
    Predicate<PsiFile> filter = null;

    PsiClass[] classes = clsFile.getClasses();
    if (classes.length > 0 && classes[0] instanceof ClsClassImpl cls) {
      String sourceFileName = cls.getSourceFileName();
      String packageName = clsFile.getPackageName();
      String relativePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;
      LanguageLevel level = JavaMultiReleaseUtil.getVersion(clsFile);
      if (level == null) {
        finder = root -> root.findFileByRelativePath(relativePath);
      }
      else {
        // Multi-release jar: assume that source file is placed in META-INF/versions/<ver>
        // fallback to default location only if there's no the same file in the root
        String versionPath = "META-INF/versions/" + level.feature() + "/" + relativePath;
        if (JavaMultiReleaseUtil.findBaseFile(clsFile.getVirtualFile()) != null) {
          finder = root -> root.findFileByRelativePath(versionPath);
        } else {
          finder = root -> {
            VirtualFile target = root.findFileByRelativePath(versionPath);
            return target == null ? root.findFileByRelativePath(relativePath) : target;
          };
        }
      }
      filter = PsiClassOwner.class::isInstance;
    }
    else {
      PsiJavaModule module = clsFile.getModuleDeclaration();
      if (module != null) {
        String moduleName = module.getName();
        finder = root -> !JrtFileSystem.isModuleRoot(root) || moduleName.equals(root.getName()) ? root.findChild(PsiJavaModule.MODULE_INFO_FILE) : null;
        filter = psi -> {
          PsiJavaModule candidate = psi instanceof PsiJavaFile ? ((PsiJavaFile)psi).getModuleDeclaration() : null;
          return candidate != null && moduleName.equals(candidate.getName());
        };
      }
    }

    if (finder == null) return clsFile;

    return findSourceRoots(clsFile.getContainingFile().getVirtualFile())
      .map(finder)
      .filter(source -> source != null && source.isValid())
      .map(PsiManager.getInstance(myProject)::findFile)
      .filter(filter)
      .findFirst()
      .orElse(clsFile);
  }

  private Stream<VirtualFile> findSourceRoots(VirtualFile file) {
    Stream<VirtualFile> modelRoots = ProjectFileIndex.getInstance(myProject).getOrderEntriesForFile(file).stream()
      .filter(entry -> entry instanceof LibraryOrSdkOrderEntry && entry.isValid())
      .flatMap(entry -> Stream.of(((LibraryOrSdkOrderEntry)entry).getRootFiles(OrderRootType.SOURCES)));

    Stream<VirtualFile> synthRoots = AdditionalLibraryRootsProvider.EP_NAME.getExtensionList().stream()
      .flatMap(provider -> provider.getAdditionalProjectLibraries(myProject).stream())
      .filter(library -> library.contains(file, false, true))
      .flatMap(library -> library.getSourceRoots().stream());

    return Stream.concat(modelRoots, synthRoots);
  }

  @Override
  public @NotNull LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile) {
    // For default project, do not look into virtual file system.
    // It is important for Upsource, where operations are done in default project to
    // prevent expensive look-up into VFS
    if (virtualFile == null || myProject.isDefault()) return PsiUtil.getLanguageLevel(myProject);

    LanguageLevel level = JavaLanguageLevelPusher.getPushedLanguageLevel(virtualFile);
    if (level != null) {
      return level;
    }

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    Module module = index.getModuleForFile(virtualFile);
    if (module != null && index.isInSourceContent(virtualFile)) {
      return LanguageLevelUtil.getEffectiveLanguageLevel(module);
    }

    if (virtualFile instanceof LightVirtualFile) {
      return LanguageLevel.HIGHEST;
    }

    LanguageLevel classesLanguageLevel = getClassesLanguageLevel(virtualFile);
    return classesLanguageLevel != null ? classesLanguageLevel : PsiUtil.getLanguageLevel(myProject);
  }

  /**
   * For files under a library source root, returns the language level configured for the corresponding classes root.
   *
   * @param virtualFile virtual file for which language level is requested.
   * @return language level for classes root or null if file is not under a library source root or no matching classes root is found.
   */
  private @Nullable LanguageLevel getClassesLanguageLevel(VirtualFile virtualFile) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile sourceRoot = index.getSourceRootForFile(virtualFile);
    VirtualFile folder = virtualFile.isDirectory() ? virtualFile : virtualFile.getParent();
    if (sourceRoot != null && sourceRoot.isDirectory() && folder != null) {
      String relativePath = VfsUtilCore.getRelativePath(folder, sourceRoot, '/');
      if (relativePath == null) {
        LOG.error("Null relative path: folder=" + folder + "; root=" + sourceRoot);
        return null;
      }
      String className = virtualFile.getNameWithoutExtension();
      Set<VirtualFile> visitedRoots = new HashSet<>();
      for (OrderEntry entry : index.getOrderEntriesForFile(virtualFile)) {
        if (!(entry instanceof LibraryOrSdkOrderEntry libraryOrSdkEntry)) continue;
        for (VirtualFile rootFile : libraryOrSdkEntry.getRootFiles(OrderRootType.CLASSES)) {
          if (visitedRoots.add(rootFile)) {
            VirtualFile classFile = rootFile.findFileByRelativePath(relativePath);
            PsiJavaFile javaFile = classFile == null ? null : getPsiFileInRoot(classFile, className);
            if (javaFile != null) {
              return javaFile.getLanguageLevel();
            }
          }
        }
      }
      return LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel();
    }
    return null;
  }

  private @Nullable PsiJavaFile getPsiFileInRoot(final VirtualFile dirFile, @Nullable String className) {
    if (className != null) {
      final VirtualFile classFile = dirFile.findChild(StringUtil.getQualifiedName(className, StdFileTypes.CLASS.getDefaultExtension()));
      if (classFile != null) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(classFile);
        if (psiFile instanceof PsiJavaFile) {
          return (PsiJavaFile)psiFile;
        }
      }
    }

    final VirtualFile[] children = dirFile.getChildren();
    for (VirtualFile child : children) {
      if (FileTypeRegistry.getInstance().isFileOfType(child, StdFileTypes.CLASS) && child.isValid()) {
        final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(child);
        if (psiFile instanceof PsiJavaFile) {
          return (PsiJavaFile)psiFile;
        }
      }
    }
    return null;
  }

  @Override
  public ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement) {
    ImportHelper importHelper = new ImportHelper(JavaCodeStyleSettings.getInstance(statement.getContainingFile()));
    return importHelper.getDefaultAnchor(list, statement);
  }

  @Override
  public @Nullable PsiElement getDefaultMemberAnchor(@NotNull PsiClass aClass, @NotNull PsiMember member) {
    CodeStyleSettings settings = CodeStyle.getSettings(aClass.getContainingFile());
    MemberOrderService service = ApplicationManager.getApplication().getService(MemberOrderService.class);
    PsiElement anchor = null;
    if (!(aClass instanceof PsiImplicitClass)) {
      anchor = service.getAnchor(member, settings.getCommonSettings(JavaLanguage.INSTANCE), aClass);
    }

    PsiElement newAnchor = skipWhitespaces(aClass, anchor);
    if (newAnchor != null) {
      return newAnchor;
    }

    if (anchor != null && anchor != aClass) {
      anchor = anchor.getNextSibling();
      while (anchor instanceof PsiJavaToken && (anchor.getText().equals(",") || anchor.getText().equals(";"))) {
        final boolean afterComma = anchor.getText().equals(",");
        anchor = anchor.getNextSibling();
        if (afterComma) {
          newAnchor = skipWhitespaces(aClass, anchor);
          if (newAnchor != null) return newAnchor;
        }
      }
      if (anchor != null) {
        return anchor;
      }
    }

    // The main idea is to avoid to anchor to 'white space' element because that causes reformatting algorithm
    // to perform incorrectly. The algorithm is encapsulated at the PostprocessReformattingAspect.doPostponedFormattingInner().
    final PsiElement lBrace = aClass.getLBrace();
    if (lBrace != null) {
      PsiElement result = lBrace.getNextSibling();
      while (result instanceof PsiWhiteSpace) {
        result = result.getNextSibling();
      }
      return result;
    }

    return aClass.getRBrace();
  }

  private static PsiElement skipWhitespaces(PsiClass aClass, PsiElement anchor) {
    if (anchor != null && PsiTreeUtil.skipWhitespacesForward(anchor) == aClass.getRBrace()) {
      // Given member should be inserted as the last child.
      return aClass.getRBrace();
    }
    return null;
  }

  @Override
  public void setupCatchBlock(@NotNull String exceptionName, @NotNull PsiType exceptionType, PsiElement context, @NotNull PsiCatchSection catchSection) {
    FileTemplate template = FileTemplateManager.getInstance(catchSection.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    FileTemplate declarationTemplate = FileTemplateManager.getInstance(catchSection.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_DECLARATION);

    Properties props = FileTemplateManager.getInstance(myProject).getDefaultProperties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION_TYPE, exceptionType.getCanonicalText());
    if (context != null && context.isPhysical()) {
      PsiDirectory directory = context.getContainingFile().getContainingDirectory();
      if (directory != null) {
        JavaTemplateUtil.setPackageNameAttribute(props, directory);
      }
    }

    try {
      PsiTryStatement tryStmt = (PsiTryStatement)PsiElementFactory.getInstance(myProject)
        .createStatementFromText("try {} catch (" + declarationTemplate.getText(props) + ") {\n}", null);
      PsiParameter parameter = tryStmt.getCatchSections()[0].getParameter();

      String parameterName = parameter == null ? null : parameter.getName();
      if (parameterName != null) {
        if (!exceptionName.equals(parameterName)) {
          parameterName = JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(parameterName, context, false);
          props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, parameterName);
          parameter.setName(parameterName);
        }

        PsiParameter sectionParameter = catchSection.getParameter();
        if (sectionParameter != null) {
          sectionParameter.replace(parameter);
        }
      }

      PsiCodeBlock block =
        PsiElementFactory.getInstance(myProject).createCodeBlockFromText("{\n" + template.getText(props) + "\n}", null);
      Objects.requireNonNull(catchSection.getCatchBlock()).replace(block);
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Incorrect file template", (Throwable)e);
    }
  }

  @Override
  public @NotNull PsiSymbolReference getSnippetRegionSymbol(@NotNull PsiSnippetAttributeValue value) {
    return new PsiSymbolReference() {
      @Override
      public @NotNull PsiElement getElement() {
        return value;
      }

      @Override
      public @NotNull TextRange getRangeInElement() {
        return ((PsiSnippetAttributeValueImpl)value).getValueRange();
      }

      @Override
      public @NotNull Collection<? extends Symbol> resolveReference() {
        PsiSnippetDocTagValue snippet = PsiTreeUtil.getParentOfType(value, PsiSnippetDocTagValue.class);
        if (snippet == null) return List.of();
        SnippetMarkup markup = SnippetMarkup.fromSnippet(snippet);
        if (markup == null) return List.of();
        String region = value.getValue();
        SnippetMarkup.MarkupNode start = markup.getRegionStart(region);
        if (start == null) return List.of();
        PsiElement markupContext = markup.getContext();
        PsiFile file = markupContext.getContainingFile();
        if (file == null) return List.of();
        return List.of(
          new SnippetRegionSymbol(file,
                                  start.range().shiftRight(markupContext.getTextRange().getStartOffset())));
      }
    };
  }

  @Override
  public @NotNull PsiSymbolReference getInheritDocSymbol(@NotNull PsiDocToken token) {
    return new PsiSymbolReference() {
      @Override
      public @NotNull PsiElement getElement() {
        return token;
      }

      @Override
      public @NotNull TextRange getRangeInElement() {
        return token.getTextRangeInParent().shiftLeft(1);
      }

      @Override
      public @NotNull Collection<? extends Symbol> resolveReference() {
        final PsiDocComment docComment = PsiTreeUtil.getParentOfType(token, PsiDocComment.class);
        if (docComment == null) return List.of();
        if (docComment.getOwner() instanceof PsiMethod method) {
          var containingClass = method.getContainingClass();
          if (containingClass == null) return List.of();
          final var parent = token.getParent();
          if (!(parent instanceof PsiDocTag docTag)) return List.of();
          final var valueElement = docTag.getValueElement();
          final var tagLocator = new JavaDocInfoGenerator.AnyInheritDocTagLocator(docTag, method);
          final var context = JavaSuperTypeSearchUtil.INSTANCE.automaticSupertypeSearch(containingClass, method, valueElement, tagLocator);
          if (context != null && context.element() != null) {
            return List.of(new SnippetRegionSymbol(context.element().getContainingFile(), getSnippetRange(context.element())));
          }
        }

        return List.of();
      }

      private static TextRange getSnippetRange(PsiElement target) {
        if (target instanceof PsiDocTag) {
          final var lines = target.getText().split("\n");
          if (lines.length > 1 && lines[lines.length - 1].matches("\\s*\\*\\s*")) {
            return target.getTextRange().grown(-lines[lines.length - 1].length());
          }
        }
        return target.getTextRange();
      }
    };
  }

  public static final class SnippetRegionSymbol implements Symbol, DocumentationTarget, NavigationTarget {

    private final @NotNull PsiFile myFile;
    private final @NotNull TextRange myRangeInFile;

    private SnippetRegionSymbol(@NotNull PsiFile file, @NotNull TextRange rangeInFile) {
      myFile = file;
      myRangeInFile = rangeInFile;
    }

    @Override
    public @NotNull Pointer<SnippetRegionSymbol> createPointer() {
      return Pointer.fileRangePointer(myFile, myRangeInFile, SnippetRegionSymbol::new);
    }

    private @NlsSafe @NotNull String getText() {
      return myRangeInFile.substring(myFile.getText());
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
      VirtualFile virtualFile = myFile.getVirtualFile();
      return TargetPresentation.builder(getText())
        .locationText(virtualFile.getName(), virtualFile.getFileType().getIcon())
        .presentation();
    }

    @Override
    public @NotNull @NlsContexts.HintText String computeDocumentationHint() {
      return getText();
    }

    @Override
    public @Nullable NavigationRequest navigationRequest() {
      return NavigationRequest.sourceNavigationRequest(myFile, myRangeInFile);
    }
  }
}

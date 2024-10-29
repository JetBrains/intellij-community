// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoFileItemProvider;
import com.intellij.ide.actions.NonProjectScopeDisablerEP;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.DirectoryFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.IdeUICustomization;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Model for "Go to | File" action
 */
public class GotoFileModel extends FilteringGotoByModel<FileTypeRef> implements DumbAware, Comparator<Object> {
  private final int myMaxSize;
  private final Predicate<PsiFileSystemItem> myCustomFilter;

  public GotoFileModel(@NotNull Project project) {
    super(project, ChooseByNameContributor.FILE_EP_NAME.getExtensionList());
    myCustomFilter = createCustomFilter(project, GotoFileCustomizer.EP_NAME.getExtensionList());
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      myMaxSize = Integer.MAX_VALUE;
    }
    else {
      IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(project);
      myMaxSize = frame != null ? frame.getSize().width : Integer.MAX_VALUE;
    }
  }

  public boolean isSlashlessMatchingEnabled() {
    return true;
  }

  @Override
  public @NotNull ChooseByNameItemProvider getItemProvider(@Nullable PsiElement context) {
    for (GotoFileCustomizer customizer : GotoFileCustomizer.EP_NAME.getExtensionList()) {
      GotoFileItemProvider provider = customizer.createItemProvider(myProject, context, this);
      if (provider != null) return provider;
    }
    return new GotoFileItemProvider(myProject, context, this);
  }

  @Override
  protected boolean acceptItem(final NavigationItem item) {
    if (item instanceof PsiFileSystemItem fsi && !myCustomFilter.test(fsi)) {
      return false;
    }
    if (item instanceof PsiFile file) {
      final Collection<FileTypeRef> types = getFilterItems();
      // if language substitutors are used, PsiFile.getFileType() can be different from
      // PsiFile.getVirtualFile().getFileType()
      if (types != null) {
        if (types.contains(FileTypeRef.forFileType(file.getFileType()))) return true;
        VirtualFile vFile = file.getVirtualFile();
        return vFile != null && types.contains(FileTypeRef.forFileType(vFile.getFileType()));
      }
      return true;
    } else if (item instanceof PsiDirectory) {
      final Collection<FileTypeRef> types = getFilterItems();
      if (types != null) return types.contains(DIRECTORY_FILE_TYPE_REF);
      return true;
    }
    else {
      return super.acceptItem(item);
    }
  }

  @Override
  protected @Nullable FileTypeRef filterValueFor(NavigationItem item) {
    return item instanceof PsiFile ? FileTypeRef.forFileType(((PsiFile) item).getFileType()) : null;
  }

  @Override
  public String getPromptText() {
    return IdeBundle.message("prompt.gotofile.enter.file.name");
  }

  @Override
  public String getCheckBoxName() {
    if (NonProjectScopeDisablerEP.EP_NAME.getExtensionList().stream().anyMatch(ep -> ep.disable)) {
      return null;
    }
    return IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.files");
  }

  @Override
  public @NotNull String getNotInMessage() {
    return "";
  }

  @Override
  public @NotNull String getNotFoundMessage() {
    return IdeBundle.message("label.no.files.found");
  }

  @Override
  public boolean loadInitialCheckBoxState() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    return propertiesComponent.isTrueValue("GoToClass.toSaveIncludeLibraries") &&
           propertiesComponent.isTrueValue("GoToFile.includeJavaFiles");
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    if (propertiesComponent.isTrueValue("GoToClass.toSaveIncludeLibraries")) {
      propertiesComponent.setValue("GoToFile.includeJavaFiles", Boolean.toString(state));
    }
  }

  @Override
  public @NotNull PsiElementListCellRenderer getListCellRenderer() {
    return new GotoFileCellRenderer(myMaxSize) {
      @Override
      public @NotNull ItemMatchers getItemMatchers(@NotNull JList list, @NotNull Object value) {
        ItemMatchers defaultMatchers = super.getItemMatchers(list, value);
        if (!(value instanceof PsiFileSystemItem)) return defaultMatchers;

        return convertToFileItemMatchers(defaultMatchers, (PsiFileSystemItem) value, GotoFileModel.this);
      }
    };
  }

  @Override
  public @Nullable String getFullName(final @NotNull Object element) {
    return element instanceof PsiFileSystemItem ? getFullName(((PsiFileSystemItem)element).getVirtualFile()) : getElementName(element);
  }

  public @Nullable String getFullName(@NotNull VirtualFile file) {
    VirtualFile root = getTopLevelRoot(file);
    return root != null ? GotoFileCellRenderer.getRelativePathFromRoot(file, root)
                        : GotoFileCellRenderer.getRelativePath(file, myProject);
  }

  public @Nullable VirtualFile getTopLevelRoot(@NotNull VirtualFile file) {
    VirtualFile root = getContentRoot(file);
    return root == null ? null : JBIterable.generate(root, r -> getContentRoot(r.getParent())).last();
  }

  private VirtualFile getContentRoot(@Nullable VirtualFile file) {
    return file == null ? null : GotoFileCellRenderer.getAnyRoot(file, myProject);
  }

  @Override
  public String @NotNull [] getSeparators() {
    return new String[] {"/", "\\"};
  }

  @Override
  public String getHelpId() {
    return "procedures.navigating.goto.class";
  }

  @Override
  public boolean willOpenEditor() {
    return true;
  }

  @Override
  public @NotNull String removeModelSpecificMarkup(@NotNull String pattern) {
    if (pattern.endsWith("/") || pattern.endsWith("\\")) {
      return pattern.substring(0, pattern.length() - 1);
    }
    return pattern;
  }

  /** Just to remove smartness from {@link ChooseByNameBase#calcSelectedIndex} */
  @Override
  public int compare(Object o1, Object o2) {
    return 0;
  }

  public static @NotNull PsiElementListCellRenderer.ItemMatchers convertToFileItemMatchers(@NotNull PsiElementListCellRenderer.ItemMatchers defaultMatchers,
                                                                                           @NotNull PsiFileSystemItem value,
                                                                                           @NotNull GotoFileModel model) {
    String shortName = model.getElementName(value);
    if (shortName != null && defaultMatchers.nameMatcher instanceof MinusculeMatcher) {
      String sanitized = GotoFileItemProvider
        .getSanitizedPattern(((MinusculeMatcher)defaultMatchers.nameMatcher).getPattern(), model);
      for (int i = sanitized.lastIndexOf('/') + 1; i < sanitized.length() - 1; i++) {
        MinusculeMatcher nameMatcher = NameUtil.buildMatcher("*" + sanitized.substring(i), NameUtil.MatchingCaseSensitivity.NONE);
        if (nameMatcher.matches(shortName)) {
          String locationPattern = FileUtil.toSystemDependentName(StringUtil.trimEnd(sanitized.substring(0, i), "/"));
          return new PsiElementListCellRenderer.ItemMatchers(nameMatcher, GotoFileItemProvider.getQualifiedNameMatcher(locationPattern));
        }
      }
    }

    return defaultMatchers;
  }

  public static final FileTypeRef DIRECTORY_FILE_TYPE_REF = FileTypeRef.forFileType(new DirectoryFileType() {
    @Override
    public @NonNls @NotNull String getName() {
      return IdeBundle.message("search.everywhere.directory.file.type.name");
    }

    @Override
    public @NlsContexts.Label @NotNull String getDescription() {
      return IdeBundle.message("filetype.search.everywhere.directory.description");
    }

    @Override
    public @NlsSafe @NotNull String getDefaultExtension() {
      return "";
    }

    @Override
    public Icon getIcon() {
      return PlatformIcons.FOLDER_ICON;
    }

    @Override
    public boolean isBinary() {
      return false;
    }
  });

  private static @NotNull Predicate<PsiFileSystemItem> createCustomFilter(@NotNull Project project, @NotNull List<GotoFileCustomizer> customizers) {
    return fsi -> {
      for (GotoFileCustomizer customizer : customizers) {
        if (!customizer.isAccepted(project, fsi)) return false;
      }
      return true;
    };
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.icons.AllIcons;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.java.stubs.index.JavaAutoModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSourceModuleNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

@NotNullByDefault
final class ModuleReferenceItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (context.isSmart()) return;
    PsiElement position = context.getPosition();
    PsiElement parent = position.getParent();
    if (parent instanceof PsiJavaModuleReferenceElement) {
      addModuleReferences(parent, position, context.getOriginalFile(), sink);
    }
  }

  private static void addModuleReferences(PsiElement moduleRef, PsiElement position, PsiFile originalFile, ModCompletionResult result) {
    PsiElement statement = moduleRef.getParent();
    boolean checkAccess = statement instanceof PsiImportModuleStatement;
    boolean withAutoModules = checkAccess || statement instanceof PsiRequiresStatement;
    if (withAutoModules || statement instanceof PsiPackageAccessibilityStatement) {
      PsiElement parent = statement.getParent();
      if (parent != null) {
        Project project = moduleRef.getProject();
        Set<String> filter = new HashSet<>();
        Function<@Nullable PsiJavaModule, @Nullable String> getModuleName = psiJavaModule -> {
          if (psiJavaModule == null) return null;
          return psiJavaModule.getName();
        };
        String currentJavaModuleName = getModuleName.apply(PsiTreeUtil.getParentOfType(statement, PsiJavaModule.class));
        if (currentJavaModuleName == null) {
          currentJavaModuleName = getModuleName.apply(
            JavaPsiModuleUtil.findDescriptorByElement(originalFile));
        }
        if (currentJavaModuleName == null) currentJavaModuleName = findModuleName(originalFile, position);

        if (currentJavaModuleName != null) {
          // "importing a module declaration" can declare its own module.
          if (statement instanceof PsiImportModuleStatement) {
            CommonCompletionItem item = createModuleItem(currentJavaModuleName).withTail(ModNavigatorTailType.semicolonType());
            result.accept(item);
          }

          filter.add(currentJavaModuleName);
        }

        JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
        GlobalSearchScope scope = ProjectScope.getAllScope(project);
        for (@NlsSafe String name : index.getAllKeys(project)) {
          Collection<PsiJavaModule> modules = index.getModules(name, project, scope);
          if (!modules.isEmpty() && filter.add(name)) {
            CommonCompletionItem item = createModuleItem(name);
            if (checkAccess && !ContainerUtil.and(modules, module -> JavaModuleGraphUtil.isModuleReadable(parent, module))) {
              item = markAsInaccessible(item);
            }
            if (withAutoModules) item = item.withTail(ModNavigatorTailType.semicolonType());
            result.accept(item);
          }
        }

        if (withAutoModules) {
          Module module = ModuleUtilCore.findModuleForFile(originalFile);
          if (module != null) {
            scope = ProjectScope.getAllScope(project);
            for (String name : JavaSourceModuleNameIndex.getAllKeys(project)) {
              Collection<VirtualFile> manifests = JavaSourceModuleNameIndex.getFilesByKey(name, scope);
              if (!manifests.isEmpty()) {
                CommonCompletionItem item = getAutoModuleReference(name, parent, filter);
                if (item != null) {
                  if (!checkAccess) {
                    item = item.withPriority(-1);
                  }
                  else if (!ContainerUtil.and(manifests, manifest -> JavaModuleGraphUtil.isModuleReadable(parent, manifest))) {
                    item = markAsInaccessible(item);
                  }
                  result.accept(item);
                }
              }
            }
            for (String name : JavaAutoModuleNameIndex.getAllKeys(project)) {
              Collection<VirtualFile> files = JavaAutoModuleNameIndex.getFilesByKey(name, scope);
              if (!files.isEmpty()) {
                CommonCompletionItem item = getAutoModuleReference(name, parent, filter);
                if (item != null) {
                  if (!checkAccess) {
                    item = item.withPriority(-1);
                  }
                  else if (!ContainerUtil.and(files, file -> JavaModuleGraphUtil.isModuleReadable(parent, file))) {
                    item = markAsInaccessible(item);
                  }
                  result.accept(item);
                }
              }
            }
          }
        }
      }
    }
  }

  private static CommonCompletionItem createModuleItem(@NlsSafe String name) {
    return new CommonCompletionItem(name)
      .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(name)).withMainIcon(() -> AllIcons.Nodes.JavaModule));
  }

  /**
   * Searching for a module name in a broken PsiFile when import module declaration typing before the module description.
   * <pre>{@code
   *   import module current.<caret>
   *   module current.module.name {
   *   }
   * }</pre>
   *
   * @param originalFile The module-info.java file
   * @param position     the position within the file
   * @return The module name if found, otherwise null.
   */
  private static @Nullable String findModuleName(PsiFile originalFile, PsiElement position) {
    if (!PsiJavaModule.MODULE_INFO_FILE.equals(originalFile.getName())) return null;
    if (!(position.getNode() instanceof PsiIdentifier intellijIdeaRulezzz)) return null;
    StringBuilder name = new StringBuilder();
    PsiElement cursor = intellijIdeaRulezzz;
    PsiElement prev = null;
    while ((cursor = cursor.getNextSibling()) != null) {
      if (cursor instanceof PsiErrorElement) {
        name.setLength(0);
      }
      else if (cursor instanceof PsiIdentifier && prev instanceof PsiIdentifier) {
        name.setLength(0);
        name.append(cursor.getText());
      }
      else if (!(cursor instanceof PsiWhiteSpace)) {
        name.append(cursor.getText());
      }
      prev = cursor;
    }
    String result = name.toString();
    if (result.trim().isEmpty()) return null;
    return result;
  }

  private static @Nullable CommonCompletionItem getAutoModuleReference(@NlsSafe String name, PsiElement parent,
                                                                       Set<? super String> filter) {
    if (PsiNameHelper.isValidModuleName(name, parent) && filter.add(name)) {
      return new CommonCompletionItem(name)
        .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(name))
                            .withMainIcon(() -> AllIcons.FileTypes.Archive))
        .withTail(ModNavigatorTailType.semicolonType());
    }
    return null;
  }

  /**
   * Marks the given {@code CommonCompletionItem} as inaccessible.
   *
   * @param item the {@link CommonCompletionItem} to be marked as inaccessible
   * @return the modified {@link CommonCompletionItem} marked as inaccessible
   */
  private static CommonCompletionItem markAsInaccessible(CommonCompletionItem item) {
    ModCompletionItemPresentation oldPresentation = item.presentation();
    return item.withPriority(-2)
      .withPresentation(oldPresentation.withMainText(oldPresentation.mainText().highlightAll(MarkupText.Kind.ERROR)));
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}

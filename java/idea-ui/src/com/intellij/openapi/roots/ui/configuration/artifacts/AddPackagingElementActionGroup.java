package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AddPackagingElementActionGroup extends ActionGroup {
  private final ArtifactEditorEx myArtifactEditor;

  public AddPackagingElementActionGroup(ArtifactEditorEx artifactEditor) {
    super(ProjectBundle.message("artifacts.add.copy.action"), true);
    myArtifactEditor = artifactEditor;
    getTemplatePresentation().setIcon(Icons.ADD_ICON);
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;

    List<AnAction> actions = new ArrayList<AnAction>();
    final PackagingElementType<?>[] types = PackagingElementFactory.getInstance().getNonCompositeElementTypes();
    for (final PackagingElementType<?> type : types) {
      if (!type.canCreate(myArtifactEditor.getContext(), myArtifactEditor.getArtifact())) continue;

      final AnAction action = new AnAction(type.getPresentableName()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myArtifactEditor.addNewPackagingElement(type);
        }
      };
      action.getTemplatePresentation().setIcon(type.getCreateElementIcon());
      actions.add(action);
    }
    return actions.toArray(new AnAction[actions.size()]);
  }

}

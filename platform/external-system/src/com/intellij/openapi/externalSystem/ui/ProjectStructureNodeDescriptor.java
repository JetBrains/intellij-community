package com.intellij.openapi.externalSystem.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Descriptor for the node of 'project structure view' derived from gradle.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:47 PM
 * @param <T>   target element type
 */
public class ProjectStructureNodeDescriptor<T extends ProjectEntityId> extends PresentableNodeDescriptor<T> {

  private TextAttributesKey myAttributes = ExternalSystemTextAttributes.NO_CHANGE;

  private final T myId;

  @SuppressWarnings("NullableProblems")
  public ProjectStructureNodeDescriptor(@NotNull T id, @NotNull String text, @Nullable Icon icon) {
    super(null, null);
    myId = id;
    setIcon(icon);
    myName = text;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setAttributesKey(myAttributes);
    presentation.setPresentableText(myName);
    presentation.setIcon(getIcon());
  }

  @NotNull
  @Override
  public T getElement() {
    return myId;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public TextAttributesKey getAttributes() {
    return myAttributes;
  }

  public void setAttributes(@NotNull TextAttributesKey attributes) {
    myAttributes = attributes;
    if (attributes == ExternalSystemTextAttributes.NO_CHANGE || attributes == ExternalSystemTextAttributes.IDE_LOCAL_CHANGE) {
      myId.setOwner(ProjectSystemId.IDE);
    }
    update();
  }

  public void setToolTip(@NotNull String text) {
    getTemplatePresentation().setTooltip(text);
    update();
  }
}

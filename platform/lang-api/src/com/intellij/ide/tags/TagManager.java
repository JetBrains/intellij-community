package com.intellij.ide.tags;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;

/**
 *
 *
 * @author gregsh
 */
@ApiStatus.Experimental
public abstract class TagManager {

  @NotNull
  public static TagManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TagManager.class);
  }

  public static boolean isEnabled() {
    return Registry.is("ide.element.tags.enabled");
  }

  @Nullable
  public static Icon appendTags(@Nullable PsiElement element, @NotNull ColoredTextContainer component) {
    Collection<TagManager.Tag> tags = getElementTags(element);
    Icon tagIcon = null;
    for (TagManager.Tag tag : tags) {
      if (tagIcon == null) tagIcon = tag.icon;
      if (tag.text.isEmpty()) continue;
      component.append(tag.text + " ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        .derive(SimpleTextAttributes.STYLE_BOLD, tag.color, null, null));
    }
    return tagIcon;
  }

  public static @NotNull Collection<Tag> getElementTags(@Nullable PsiElement element) {
    if (!isEnabled()) return Collections.emptySet();
    if (element == null || !element.isValid()) return Collections.emptySet();
    return getInstance(element.getProject()).getTags(element);
  }

  @NotNull
  public abstract Collection<Tag> getTags(@NotNull PsiElement element);


  @ApiStatus.Experimental
  public static final class Tag {
    public final @Nls String text;
    public final Color color;
    public final Icon icon;

    public Tag(@NotNull @Nls String text, @Nullable Color color, @Nullable Icon icon) {
      this.text = text;
      this.color = color;
      this.icon = icon;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      return o instanceof Tag && text.equals(((Tag)o).text);
    }

    @Override
    public int hashCode() {
      return text.hashCode();
    }
  }
}

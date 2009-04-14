package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class PresentableNodeDescriptor<E> extends NodeDescriptor<E>  {

  private static PresentationData EMPTY = new PresentationData();

  private PresentationData myPresenation;

  protected PresentableNodeDescriptor(Project project, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
  }

  public final boolean update() {
    if (!shouldUpdateData()) return false;

    PresentationData presentation = executeUpdate();

    Icon openIcon = presentation.getIcon(true);
    Icon closedIcon = presentation.getIcon(false);
    String name = presentation.getPresentableText();
    Color color = presentation.getForcedTextForeground();

    myOpenIcon = openIcon;
    myClosedIcon = closedIcon;
    myName = name;
    myColor = color;

    boolean updated = presentation.equals(myPresenation);

    myPresenation = presentation;

    return updated;
  }

  private PresentationData executeUpdate() {
    PresentationData presentation = createPresentation();
    update(presentation);
    postprocess(presentation);
    return presentation;
  }

  @NotNull
  protected PresentationData createPresentation() {
    return new PresentationData();
  }

  protected void postprocess(PresentationData date) {

  }

  protected abstract void update(PresentationData presentation);

  protected boolean shouldUpdateData() {
    return true;
  }

  @NotNull
  public PresentationData getPresentation() {
    return myPresenation != null ? myPresenation : EMPTY;
  }

  public static class ColoredFragment {
    private final String myText;
    private final String myToolTip;
    private final SimpleTextAttributes myAttributes;

    public ColoredFragment(String aText, SimpleTextAttributes aAttributes) {
      this(aText, null, aAttributes);
    }

    public ColoredFragment(String aText, String toolTip, SimpleTextAttributes aAttributes) {
      myText = aText == null? "" : aText;
      myAttributes = aAttributes;
      myToolTip = toolTip;
    }

    public String getToolTip() {
      return myToolTip;
    }

    public String getText() {
      return myText;
    }

    public SimpleTextAttributes getAttributes() {
      return myAttributes;
    }


    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ColoredFragment that = (ColoredFragment)o;

      if (myAttributes != null ? !myAttributes.equals(that.myAttributes) : that.myAttributes != null) return false;
      if (myText != null ? !myText.equals(that.myText) : that.myText != null) return false;
      if (myToolTip != null ? !myToolTip.equals(that.myToolTip) : that.myToolTip != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (myText != null ? myText.hashCode() : 0);
      result = 31 * result + (myToolTip != null ? myToolTip.hashCode() : 0);
      result = 31 * result + (myAttributes != null ? myAttributes.hashCode() : 0);
      return result;
    }
  }


  /**
   * @deprecated @see getPresentation()
   * @return
   */
  protected String getToolTip() {
    return getPresentation().getTooltip();
  }

  /**
   * @deprecated @see getPresentation()
   * @return
   */
  @Nullable
  public TextAttributesKey getAttributesKey() {
    return getPresentation().getTextAttributesKey();
  }

  /**
   * @deprecated @see getPresentation()
   * @return
   */
  @Nullable
  public String getLocationString() {
    return getPresentation().getLocationString();
  }

}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.roots.ui.ModifiableCellAppearanceEx;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// todo: move to intellij.platform.lang.impl ?
public class CompositeAppearance implements ModifiableCellAppearanceEx {
  private static final Logger LOG = Logger.getInstance(CompositeAppearance.class);

  private Icon myIcon;
  private final List<TextSection> mySections = new ArrayList<>();
  private int myInsertionIndex = 0;

  @Override
  public void customize(@NotNull SimpleColoredComponent component) {
    synchronized (mySections) {
      for (TextSection section : mySections) {
        final TextAttributes attributes = section.getTextAttributes();
        component.append(section.getText(), SimpleTextAttributes.fromTextAttributes(attributes));
      }
      component.setIcon(myIcon);
    }
  }

  public Icon getIcon() {
    synchronized (mySections) {
      return myIcon;
    }
  }

  @Override
  public void setIcon(@Nullable final Icon icon) {
    synchronized (mySections) {
      myIcon = icon;
    }
  }

  @Override
  @NotNull
  public String getText() {
    synchronized (mySections) {
      @Nls StringBuilder buffer = new StringBuilder();
      for (TextSection section : mySections) {
        buffer.append(section.TEXT);
      }
      return buffer.toString();
    }
  }

  public boolean equals(Object obj) {
    synchronized (mySections) {
      if (!(obj instanceof CompositeAppearance)) return false;
      CompositeAppearance appearance = (CompositeAppearance)obj;
      if (SwingUtilities.isEventDispatchThread()) {
        return appearance.mySections.equals(mySections);
      }
      else {
        return new ArrayList<>(appearance.mySections).equals(new ArrayList<>(mySections));
      }
    }
  }

  public int hashCode() {
    return getText().hashCode();
  }

  protected void addSectionAt(int index, @NotNull TextSection section) {
    synchronized (mySections) {
      mySections.add(index, section);
      for (Iterator<TextSection> iterator = mySections.iterator(); iterator.hasNext();) {
        TextSection textSection = iterator.next();
        if (textSection == null) {
          LOG.error("index: " + index + " size: " + mySections.size());
          iterator.remove();
        }
      }
    }
  }

  public DequeEnd getBeginning() {
    return new DequeBeginning();
  }

  public DequeEnd getEnding() {
    return new DequeEnding();
  }

  public DequeEnd getSuffix() {
    return new DequeSuffix();
  }

  public static CompositeAppearance textComment(@Nls String text, @Nls String comment) {
    DequeEnd ending = new CompositeAppearance().getEnding();
    ending.addText(text);
    ending.addComment(comment);
    return ending.getAppearance();
  }

  public static CompositeAppearance single(@Nls String text, SimpleTextAttributes textAttributes) {
    CompositeAppearance result = new CompositeAppearance();
    result.getEnding().addText(text, textAttributes);
    return result;
  }

  public static CompositeAppearance single(@Nls String text) {
    return single(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static CompositeAppearance invalid(@NlsSafe String absolutePath) {
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.setIcon(PlatformIcons.INVALID_ENTRY_ICON);
    appearance.getEnding().addText(absolutePath, SimpleTextAttributes.ERROR_ATTRIBUTES);
    return appearance;
  }

  @TestOnly
  public Iterator<TextSection> getSectionsIterator() {
    return mySections.iterator();
  }

  public static class TextSection {
    private static final TextAttributes DEFAULT_TEXT_ATTRIBUTES = new TextAttributes(null, null, null, null, Font.PLAIN);
    private static final String DEFAULT_TEXT = "";
    private final @Nls String TEXT;
    private final TextAttributes ATTRIBUTES;

    public TextSection(@Nls String text, TextAttributes attributes) {
      ATTRIBUTES = attributes == null ? DEFAULT_TEXT_ATTRIBUTES : attributes;
      TEXT = text == null ? DEFAULT_TEXT : text;
    }

    public @Nls String getText() {
      return TEXT;
    }

    public TextAttributes getTextAttributes() {
      return ATTRIBUTES;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof TextSection)) return false;
      TextSection section = (TextSection)obj;
      return section.ATTRIBUTES.equals(ATTRIBUTES) && section.TEXT.equals(TEXT);
    }

    public int hashCode() {
      return TEXT.hashCode();
    }
  }

  public abstract class DequeEnd {
    public void addText(@Nls String text, SimpleTextAttributes textAttributes) {
      addText(text, textAttributes.toTextAttributes());
    }

    public void addText(@Nls String text) {
      addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    public abstract void addSection(TextSection section);

    public void addText(@Nls String text, TextAttributes attributes) {
      addSection(new TextSection(text, attributes));
    }

    public void addSurrounded(@Nls String text,
                              @NlsContexts.Separator String prefix,
                              @NlsContexts.Separator String suffix,
                              SimpleTextAttributes textAttributes) {
      if (text != null && !text.trim().isEmpty()) {
        addText(prefix + text + suffix, textAttributes);
      }
    }

    public CompositeAppearance getAppearance() {
      return CompositeAppearance.this;
    }

    public void addComment(@Nls String comment, SimpleTextAttributes commentAttributes) {
      addSurrounded(comment, " (", ")", commentAttributes);
    }

    public void addComment(@Nls String comment) {
      addComment(comment, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private class DequeBeginning extends DequeEnd {
    @Override
    public void addSection(TextSection section) {
      synchronized (mySections) {
        addSectionAt(0, section);
        myInsertionIndex++;
      }
    }
  }

  private class DequeEnding extends DequeEnd {
    @Override
    public void addSection(TextSection section) {
      synchronized (mySections) {
        addSectionAt(myInsertionIndex, section);
        myInsertionIndex++;
      }
    }
  }

  private class DequeSuffix extends DequeEnd {
    @Override
    public void addSection(TextSection section) {
      synchronized (mySections) {
        addSectionAt(mySections.size(), section);
      }
    }
  }
}
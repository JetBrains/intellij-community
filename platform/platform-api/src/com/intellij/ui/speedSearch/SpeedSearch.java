// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class SpeedSearch extends SpeedSearchSupply implements KeyListener, SpeedSearchActivator {
  public static final String PUNCTUATION_MARKS = "*_-+\"'/.#$>: ,;?!@%^&";

  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  private final boolean myMatchAllOccurrences;

  private String myString = "";
  private boolean myEnabled;
  private Matcher myMatcher;
  private boolean myJustActivated = false;

  public SpeedSearch() {
    this(false);
  }

  public SpeedSearch(boolean matchAllOccurrences) {
    myMatchAllOccurrences = matchAllOccurrences;
  }

  public void type(String letter) {
    updatePattern(myString + letter);
  }

  public void backspace() {
    if (!myString.isEmpty()) {
      updatePattern(myString.substring(0, myString.length() - 1));
    }
  }

  public boolean shouldBeShowing(String string) {
    return string == null ||
           myString.isEmpty() || (myMatcher != null && myMatcher.matches(string));
  }

  public void processKeyEvent(KeyEvent e) {
    if (e.isConsumed() || !myEnabled) return;

    String old = myString;
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      if (KeymapUtil.isEventForAction(e, "EditorDeleteToWordStart")) {
        if (isHoldingFilter()) {
          while (!myString.isEmpty() && !Character.isWhitespace(myString.charAt(myString.length() - 1))) {
            backspace();
          }
          e.consume();
        }
      }
      else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
        backspace();
        e.consume();
      }
      else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        if (isHoldingFilter()) {
          updatePattern("");
          e.consume();
        }
        else if (myJustActivated) {
          // Special case: speed search was activated through the API without typing anything, should be cancelled on Esc.
          myJustActivated = false;
          update();
          e.consume();
        }
      }
    }
    else if (e.getID() == KeyEvent.KEY_TYPED) {
      if (!UIUtil.isReallyTypedEvent(e)) return;
      // key-char is good only on KEY_TYPED
      // for example: key-char on ctrl-J PRESSED is \n
      // see https://en.wikipedia.org/wiki/Control_character
      char ch = e.getKeyChar();
      if (Character.isLetterOrDigit(ch) || !startedWithWhitespace(ch) && PUNCTUATION_MARKS.indexOf(ch) != -1) {
        type(Character.toString(ch));
        e.consume();
      }
    }

    if (!old.equalsIgnoreCase(myString)) {
      update();
    }
  }

  private boolean startedWithWhitespace(char ch) {
    return !isHoldingFilter() && Character.isWhitespace(ch);
  }

  public void update() {

  }

  public void noHits() {
  }

  public boolean isHoldingFilter() {
    return myEnabled && !myString.isEmpty();
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void reset() {
    if (isHoldingFilter()) {
      updatePattern("");
    }

    if (myEnabled) {
      update();
    }
  }

  public String getFilter() {
    return myString;
  }

  public void updatePattern(final String searchText) {
    if (myString.equals(searchText)) return;

    myJustActivated = false;

    String prevString = myString;
    myString = searchText;
    try {
      myMatcher = createNewMatcher(searchText);
    }
    catch (Exception e) {
      myMatcher = null;
    }
    fireStateChanged(prevString);
  }

  protected @NotNull Matcher createNewMatcher(String searchText) {
    String pattern = "*" + searchText;
    NameUtil.MatchingCaseSensitivity caseSensitivity = NameUtil.MatchingCaseSensitivity.NONE;
    String separators = SpeedSearchUtil.getDefaultHardSeparators();
    NameUtil.MatcherBuilder builder =
      new NameUtil.MatcherBuilder(pattern)
        .withCaseSensitivity(caseSensitivity)
        .withSeparators(separators);
    if (myMatchAllOccurrences) {
      builder = builder.allOccurrences();
    }
    return builder.build();
  }

  public @Nullable Matcher getMatcher() {
    return myMatcher;
  }

  @Override
  public @Nullable Iterable<TextRange> matchingFragments(@NotNull String text) {
    if (getMatcher() instanceof MinusculeMatcher matcher) {
      return matcher.matchingFragments(text);
    }
    return null;
  }

  @Override
  public void refreshSelection() {
  }

  @Override
  public boolean isPopupActive() {
    return isHoldingFilter();
  }

  @Override
  public @Nullable String getEnteredPrefix() {
    return myString;
  }

  @Override
  public boolean isSupported() {
    return false; // Disabled by default because has to be implemented differently for every subclass.
  }

  @Override
  public boolean isAvailable() {
    return true; // Convenient default for implementations, is ignored anyway when isSupported() == false.
  }

  @Override
  public boolean isActive() {
    return isPopupActive();
  }

  protected boolean shouldBeActive() {
    return myJustActivated || isHoldingFilter();
  }

  @Override
  public @Nullable JComponent getTextField() {
    return null;
  }

  @Override
  public void activate() {
    myJustActivated = true;
    doActivate();
  }

  protected void doActivate() {
  }

  @Override
  public void addChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  private void fireStateChanged(String prevString) {
    myChangeSupport.firePropertyChange(SpeedSearchSupply.ENTERED_PREFIX_PROPERTY_NAME, prevString, getEnteredPrefix());
  }

  @Override
  public void findAndSelectElement(@NotNull String searchQuery) {

  }

  @Override
  public void keyTyped(KeyEvent e) {
    processKeyEvent(e);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    processKeyEvent(e);
  }

  @Override
  public void keyReleased(KeyEvent e) {
    processKeyEvent(e);
  }
}

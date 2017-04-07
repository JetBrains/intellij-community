/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.unusedSymbol;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiModifier;
import com.intellij.ui.ClickListener;
import com.intellij.ui.UI;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Hashtable;
import java.util.Set;
import java.util.function.Supplier;

public class VisibilityModifierChooser extends JLabel implements UserActivityProviderComponent {

  @PsiModifier.ModifierConstant private static final String[] MODIFIERS =
    new String[]{PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC};
  private final Supplier<Boolean> myCanBeEnabled;

  private final Set<ChangeListener> myListeners = new HashSet<>();
  private String myCurrentModifier;

  public VisibilityModifierChooser(@NotNull Supplier<Boolean> canBeEnabled,
                                   @NotNull String modifier,
                                   @NotNull Consumer<String> modifierChangedConsumer) {
    this(canBeEnabled, modifier, modifierChangedConsumer, MODIFIERS);
  }


  @Override
  public void setText(String text) {
    super.setText(text);
  }

  public VisibilityModifierChooser(@NotNull Supplier<Boolean> canBeEnabled,
                                   @NotNull String modifier,
                                   @NotNull Consumer<String> modifierChangedConsumer,
                                   @NotNull String[] modifiers) {
    myCanBeEnabled = canBeEnabled;
    setIcon(AllIcons.General.Combo2);
    setDisabledIcon(AllIcons.General.Combo2);
    setIconTextGap(0);
    setHorizontalTextPosition(SwingConstants.LEFT);
    myCurrentModifier = modifier;
    setText(getPresentableText(myCurrentModifier));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (!isEnabled()) return true;
        @SuppressWarnings("UseOfObsoleteCollectionType")
        Hashtable<Integer, JComponent> sliderLabels = new Hashtable<>();
        for (int i = 0; i < modifiers.length; i++) {
          sliderLabels.put(i + 1, new JLabel(getPresentableText(modifiers[i])));
        }

        JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, modifiers.length, 1);
        slider.addChangeListener(val -> {
          final String modifier = modifiers[slider.getValue() - 1];
          if (myCurrentModifier != modifier) {
            myCurrentModifier = modifier;
            modifierChangedConsumer.consume(modifier);
            setText(getPresentableText(modifier));
            fireStateChanged();
          }
        });
        slider.setLabelTable(sliderLabels);
        slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
        slider.setPreferredSize(JBUI.size(150, modifiers.length * 25));
        slider.setPaintLabels(true);
        slider.setSnapToTicks(true);
        slider.setValue(ArrayUtil.find(modifiers, myCurrentModifier) + 1);
        final JBPopup popup = JBPopupFactory.getInstance()
          .createComponentPopupBuilder(slider, null)
          .setTitle("Effective Visibility")
          .setCancelOnClickOutside(true)
          .setMovable(true)
          .createPopup();
        popup.show(new RelativePoint(VisibilityModifierChooser.this, new Point(getWidth(), 0)));
        return true;
      }
    }.installOn(this);
  }

  private void fireStateChanged() {
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(new ChangeEvent(this));
    }
  }

  private static String getPresentableText(String modifier) {
    return StringUtil.capitalize(VisibilityUtil.toPresentableText(modifier));
  }

  @Override
  public void setForeground(Color fg) {
    super.setForeground(isEnabled() ? UI.getColor("link.foreground") : fg);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled && myCanBeEnabled.get());
  }

  @Override
  public void addChangeListener(ChangeListener changeListener) {
    myListeners.add(changeListener);
  }

  @Override
  public void removeChangeListener(ChangeListener changeListener) {
    myListeners.remove(changeListener);
  }
}

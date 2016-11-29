/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiModifier;
import com.intellij.ui.ClickListener;
import com.intellij.ui.UI;
import com.intellij.ui.UserActivityProviderComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Producer;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.Hashtable;
import java.util.Set;
import java.util.function.Supplier;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedSymbolLocalInspection extends UnusedSymbolLocalInspectionBase {

  /**
   * use {@link com.intellij.codeInspection.deadCode.UnusedDeclarationInspection} instead
   */
  @Deprecated
  public UnusedSymbolLocalInspection() {
  }

  public class OptionsPanel {
    private JCheckBox myCheckLocalVariablesCheckBox;
    private JCheckBox myCheckClassesCheckBox;
    private JCheckBox myCheckFieldsCheckBox;
    private JCheckBox myCheckMethodsCheckBox;
    private JCheckBox myCheckParametersCheckBox;
    private JCheckBox myAccessors;
    private JPanel myPanel;
    private JLabel myClassVisibilityCb;
    private JLabel myFieldVisibilityCb;
    private JLabel myMethodVisibilityCb;
    private JLabel myMethodParameterVisibilityCb;
    private JCheckBox myInnerClassesCheckBox;
    private JLabel myInnerClassVisibilityCb;

    public OptionsPanel() {
      myCheckLocalVariablesCheckBox.setSelected(LOCAL_VARIABLE);
      myCheckClassesCheckBox.setSelected(CLASS);
      myCheckFieldsCheckBox.setSelected(FIELD);
      myCheckMethodsCheckBox.setSelected(METHOD);
      myInnerClassesCheckBox.setSelected(INNER_CLASS);
      myCheckParametersCheckBox.setSelected(PARAMETER);
      myAccessors.setSelected(!isIgnoreAccessors());
      updateEnableState();

      final ActionListener listener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          LOCAL_VARIABLE = myCheckLocalVariablesCheckBox.isSelected();
          CLASS = myCheckClassesCheckBox.isSelected();
          INNER_CLASS = myInnerClassesCheckBox.isSelected();
          FIELD = myCheckFieldsCheckBox.isSelected();
          METHOD = myCheckMethodsCheckBox.isSelected();
          setIgnoreAccessors(!myAccessors.isSelected());
          PARAMETER = myCheckParametersCheckBox.isSelected();

          updateEnableState();
        }
      };
      myCheckLocalVariablesCheckBox.addActionListener(listener);
      myCheckFieldsCheckBox.addActionListener(listener);
      myCheckMethodsCheckBox.addActionListener(listener);
      myCheckClassesCheckBox.addActionListener(listener);
      myCheckParametersCheckBox.addActionListener(listener);
      myInnerClassesCheckBox.addActionListener(listener);
      myAccessors.addActionListener(listener);

      ((MyLabel)myClassVisibilityCb).setupVisibilityLabel(() -> myClassVisibility, modifier -> setClassVisibility(modifier), new String[]{PsiModifier.PACKAGE_LOCAL, PsiModifier.PUBLIC});
      ((MyLabel)myInnerClassVisibilityCb).setupVisibilityLabel(() -> myInnerClassVisibility, modifier -> setInnerClassVisibility(modifier));
      ((MyLabel)myFieldVisibilityCb).setupVisibilityLabel(() -> myFieldVisibility, modifier -> setFieldVisibility(modifier));
      ((MyLabel)myMethodVisibilityCb).setupVisibilityLabel(() -> myMethodVisibility, modifier -> setMethodVisibility(modifier));
      ((MyLabel)myMethodParameterVisibilityCb).setupVisibilityLabel(() -> myParameterVisibility, modifier -> setParameterVisibility(modifier));
    }

    private void updateEnableState() {
      UIUtil.setEnabled(myClassVisibilityCb, CLASS, true);
      UIUtil.setEnabled(myInnerClassVisibilityCb, INNER_CLASS, true);
      UIUtil.setEnabled(myFieldVisibilityCb, FIELD, true);
      UIUtil.setEnabled(myMethodVisibilityCb, METHOD, true);
      UIUtil.setEnabled(myMethodParameterVisibilityCb, PARAMETER, true);
      myAccessors.setEnabled(METHOD);
    }

    public JComponent getPanel() {
      return myPanel;
    }

    private void createUIComponents() {
      myClassVisibilityCb = new MyLabel(() -> CLASS);
      myInnerClassVisibilityCb = new MyLabel(() -> INNER_CLASS);
      myFieldVisibilityCb = new MyLabel(() -> FIELD);
      myMethodVisibilityCb = new MyLabel(() -> METHOD);
      myMethodParameterVisibilityCb = new MyLabel(() -> PARAMETER);
      myAccessors = new JCheckBox() {
        @Override
        public void setEnabled(boolean b) {
          super.setEnabled(b && METHOD);
        }
      };
    }
  }

  private static class MyLabel extends JLabel implements UserActivityProviderComponent {

    @PsiModifier.ModifierConstant private static final String[] MODIFIERS =
      new String[]{PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PUBLIC};
    private final Supplier<Boolean> myCanBeEnabled;

    private Set<ChangeListener> myListeners = new HashSet<>();

    public MyLabel(Supplier<Boolean> canBeEnabled) {
      myCanBeEnabled = canBeEnabled;
      setIcon(AllIcons.General.Combo2);
      setDisabledIcon(AllIcons.General.Combo2);
      setIconTextGap(0);
      setHorizontalTextPosition(SwingConstants.LEFT);
    }

    private void fireStateChanged() {
      for (ChangeListener listener : myListeners) {
        listener.stateChanged(new ChangeEvent(this));
      }
    }

    private static String getPresentableText(String modifier) {
      return StringUtil.capitalize(VisibilityUtil.toPresentableText(modifier));
    }

    private void setupVisibilityLabel(Producer<String> visibilityProducer, Consumer<String> setter) {
      setupVisibilityLabel(visibilityProducer, setter, MODIFIERS);
    }

    private void setupVisibilityLabel(Producer<String> visibilityProducer, Consumer<String> setter, final String[] modifiers) {
      setText(getPresentableText(visibilityProducer.produce()));
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
            setter.consume(modifier);
            setText(getPresentableText(modifier));
            fireStateChanged();
          });
          slider.setLabelTable(sliderLabels);
          slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
          slider.setPreferredSize(JBUI.size(150, modifiers.length * 25));
          slider.setPaintLabels(true);
          slider.setSnapToTicks(true);
          slider.setValue(ArrayUtil.find(modifiers, visibilityProducer.produce()) + 1);
          final JBPopup popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(slider, null)
            .setTitle("Effective Visibility")
            .setCancelOnClickOutside(true)
            .setMovable(true)
            .createPopup();
          popup.show(new RelativePoint(MyLabel.this, new Point(getWidth(), 0)));
          return true;
        }
      }.installOn(this);
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

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new OptionsPanel().getPanel();
  }
}

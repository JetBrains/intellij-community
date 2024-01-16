// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.DropDownLink;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.*;

public class VariantTagFragment<T, V> extends SettingsEditorFragment<T, TagButton> {

  public void setVariantNameProvider(Function<? super V, String> variantNameProvider) {
    myVariantNameProvider = variantNameProvider;
  }

  public void setVariantHintProvider(Function<? super V, String> variantHintProvider) {
    myVariantHintProvider = variantHintProvider;
  }

  public void setVariantDescriptionProvider(Function<? super V, String> variantDescriptionProvider) {
    myVariantDescriptionProvider = variantDescriptionProvider;
  }

  public void setToggleListener(Consumer<? super V> toggleListener) {
    myToggleListener = toggleListener;
  }

  public static <T, V> VariantTagFragment<T, V> createFragment(String id,
                                                               @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                                               @Nls(capitalization = Nls.Capitalization.Title) String group,
                                                               Supplier<? extends V[]> variantsProvider,
                                                               Function<? super T, ? extends V> getter,
                                                               BiConsumer<? super T, ? super V> setter,
                                                               Predicate<? super T> initialSelection) {
    Ref<VariantTagFragment<T, V>> ref = new Ref<>();
    VariantTagButton<V> tagButton = new VariantTagButton<>(name, (e) -> ref.get().toggle(false, null));
    VariantTagFragment<T, V> fragment = new VariantTagFragment<>(id, name, group, tagButton, variantsProvider, getter, setter, initialSelection);
    tagButton.myFragment = fragment;
    Disposer.register(fragment, tagButton);
    ref.set(fragment);
    return fragment;
  }

  private V mySelectedVariant;
  private final Supplier<? extends V[]> myVariantsProvider;
  private final Function<? super T, ? extends V> myGetter;
  private final BiConsumer<? super T, ? super V> mySetter;
  private Function<? super V, String> myVariantNameProvider;
  private Function<? super V, String> myVariantHintProvider;
  private Function<? super V, String> myVariantDescriptionProvider;
  private Consumer<? super V> myToggleListener;

  public VariantTagFragment(String id,
                            @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                            @Nls(capitalization = Nls.Capitalization.Title) String group,
                            TagButton component,
                            Supplier<? extends V[]> variantsProvider,
                            Function<? super T, ? extends V> getter,
                            BiConsumer<? super T, ? super V> setter,
                            Predicate<? super T> initialSelection) {
    super(id, name, group, component, null, null, initialSelection);
    myVariantsProvider = variantsProvider;
    myGetter = getter;
    mySetter = setter;
  }

  public V getSelectedVariant() {
    return mySelectedVariant;
  }

  public void setSelectedVariant(V variant) {
    mySelectedVariant = variant;
    setSelected(!variant.equals(getVariants()[0]));
    component().updateButton(getName() + ": " + getVariantName(variant), null);
  }

  protected V[] getVariants() {
    return myVariantsProvider.get();
  }

  @Override
  public void toggle(boolean selected, AnActionEvent e) {
    super.toggle(selected, e);
    if (!selected) {
      setSelectedVariant(getVariants()[0]);
    }
  }

  @Override
  protected void resetEditorFrom(@NotNull T s) {
    setSelectedVariant(myGetter.apply(s));
  }

  @Override
  protected void applyEditorTo(@NotNull T s) {
    mySetter.accept(s, mySelectedVariant);
  }

  protected @Nls String getVariantName(V variant) {
    return myVariantNameProvider == null ? StringUtil.capitalize(variant.toString()) : myVariantNameProvider.apply(variant); //NON-NLS
  }

  protected @Nls @Nullable String getVariantHint(V variant) {
    return myVariantHintProvider == null ? null : myVariantHintProvider.apply(variant); //NON-NLS
  }

  protected @Nls String getVariantDescription(V variant) {
    return myVariantDescriptionProvider == null ? null : myVariantDescriptionProvider.apply(variant); //NON-NLS
  }

  @Override
  public boolean isTag() {
    return true;
  }

  @Override
  public @Nullable ActionGroup getCustomActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup(getName(), ContainerUtil.map(getVariants(), s ->
      new ToggleAction(getVariantName(s), getVariantHint(s), null) {

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        var description = getVariantDescription(s);

        if (description != null) {
          e.getPresentation().putClientProperty(ActionUtil.SECONDARY_TEXT, description);
        }
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return s.equals(mySelectedVariant);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        setSelectedVariant(s);
        fireEditorStateChanged();
        if (myToggleListener != null) {
          myToggleListener.accept(s);
        }
        logChange(state, e);
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    })) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().putClientProperty(ActionUtil.SECONDARY_TEXT, getVariantName(mySelectedVariant));
        e.getPresentation().setVisible(isRemovable());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    group.setPopup(true);
    return group;
  }

  private static class VariantTagButton<V> extends TagButton {

    private final DropDownLink<V> myDropDown;
    private VariantTagFragment<?, V> myFragment;

    private VariantTagButton(@Nls String text, Consumer<? super AnActionEvent> action) {
      super(text, action);
      myDropDown = new DropDownLink<>(null, link -> showPopup());
      myDropDown.setAutoHideOnDisable(false);
      add(myDropDown, JLayeredPane.POPUP_LAYER);
      myButton.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            myDropDown.dispatchEvent(e);
          }
        }

        @Override
        public void keyReleased(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            myDropDown.dispatchEvent(e);
          }
        }
      });
    }

    private JBPopup showPopup() {
      DataContext context = DataManager.getInstance().getDataContext(myDropDown);
      DefaultActionGroup group = new DefaultActionGroup(ContainerUtil.map(myFragment.getVariants(), v -> new DumbAwareAction(myFragment.getVariantName(v)) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          myFragment.setSelectedVariant(v);
          IdeFocusManager.findInstanceByComponent(myButton).requestFocus(myButton, true);
        }
      }));
      return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
    }

    @Override
    protected void layoutButtons() {
      super.layoutButtons();
      int dropDownWidth = 0;
      if (myDropDown != null) {
        Dimension preferredSize = myDropDown.getPreferredSize();
        dropDownWidth = preferredSize.width - ourInset * 2;
        myDropDown.setBounds(new Rectangle(myCloseButton.getX() - ourInset * 2, 0, preferredSize.width, myButton.getHeight()));
      }

      Insets insets = myButton.getMargin();
      insets.right += dropDownWidth;
      myButton.setMargin(insets);
      Rectangle closeButtonBounds = myCloseButton.getBounds();
      closeButtonBounds.x += dropDownWidth;
      myCloseButton.setBounds(closeButtonBounds);

      Rectangle bounds = myButton.getBounds();
      bounds.width += dropDownWidth;
      myButton.setBounds(bounds);
      setPreferredSize(bounds.getSize());
    }

    @Override
    protected void updateButton(String text, Icon icon) {
      String[] split = text.split(": ");
      myButton.setText(split[0] + ": ");
      myDropDown.setText(split.length > 1 ? split[1] : null);
      layoutButtons();
    }
  }
}

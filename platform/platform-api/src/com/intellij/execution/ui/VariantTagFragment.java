// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.*;

public class VariantTagFragment<T, V> extends SettingsEditorFragment<T, TagButton> {

  public void setVariantNameProvider(Function<? super V, String> variantNameProvider) {
    myVariantNameProvider = variantNameProvider;
  }

  public void setToggleListener(Consumer<V> toggleListener) {
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
    TagButton tagButton = new TagButton(name, () -> ref.get().toggle(false));
    VariantTagFragment<T, V> fragment = new VariantTagFragment<>(id, name, group, tagButton, variantsProvider, getter, setter, initialSelection);
    Disposer.register(fragment, tagButton);
    ref.set(fragment);
    return fragment;
  }

  private V mySelectedVariant;
  private final Supplier<? extends V[]> myVariantsProvider;
  private final Function<? super T, ? extends V> myGetter;
  private final BiConsumer<? super T, ? super V> mySetter;
  private Function<? super V, String> myVariantNameProvider;
  private Consumer<V> myToggleListener;

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
  public void toggle(boolean selected) {
    super.toggle(selected);
    if (!selected) {
      setSelectedVariant(getVariants()[0]);
    }
  }

  @Override
  protected void resetEditorFrom(@NotNull T s) {
    setSelectedVariant(myGetter.apply(s));
  }

  @Override
  protected void applyEditorTo(@NotNull T s) throws ConfigurationException {
    mySetter.accept(s, mySelectedVariant);
  }

  @Nls
  protected String getVariantName(V variant) {
    //noinspection HardCodedStringLiteral
    return myVariantNameProvider == null ? StringUtil.capitalize(variant.toString()) : myVariantNameProvider.apply(variant);
  }

  @Override
  public boolean isTag() {
    return true;
  }

  @Override
  public @Nullable ActionGroup getCustomActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup(getName(), ContainerUtil.map(getVariants(), s -> new ToggleAction(getVariantName(s)) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return s.equals(mySelectedVariant);
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        setSelectedVariant(s);
        fireEditorStateChanged();
        if (myToggleListener != null) {
          myToggleListener.accept(s);
        }
      }
    })) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().putClientProperty(Presentation.PROP_VALUE, getVariantName(mySelectedVariant));
      }
    };
    group.setPopup(true);
    return group;
  }
}

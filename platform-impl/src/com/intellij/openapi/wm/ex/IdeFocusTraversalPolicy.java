package com.intellij.openapi.wm.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.lang.reflect.Field;

public class IdeFocusTraversalPolicy extends LayoutFocusTraversalPolicyExt {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy");
  @NonNls private static final String FOCUS_TRAVERSAL_POLICY_FIELD = "focusTraversalPolicy";

  protected Component getDefaultComponentImpl(Container focusCycleRoot) {
    if (!(focusCycleRoot instanceof JComponent)) {
      return super.getDefaultComponent(focusCycleRoot);
    }
    return getPreferredFocusedComponent((JComponent)focusCycleRoot, this);
  }

  public static JComponent getPreferredFocusedComponent(@NotNull final JComponent component) {
    return getPreferredFocusedComponent(component, null);
  }

  /**
   * @return preferred focused component inside the specified <code>component</code>.
   * Method can return component itself if the <code>component</code> is legal
   * (JTextFiel)focusable
   *
   */
  public static JComponent getPreferredFocusedComponent(@NotNull final JComponent component, final FocusTraversalPolicy policyToIgnore) {
    if (!component.isVisible()) {
      return null;
    }

    final FocusTraversalPolicy focusTraversalPolicy = getFocusTraversalPolicyAwtImpl(component);
    if (focusTraversalPolicy != null && focusTraversalPolicy != policyToIgnore) {
      final Component defaultComponent = focusTraversalPolicy.getDefaultComponent(component);
      if (defaultComponent instanceof JComponent) {
        return (JComponent)defaultComponent;
      }else{
        return null;
      }
    }

    if (component instanceof JTabbedPane) {
      final JTabbedPane tabbedPane = (JTabbedPane)component;
      final Component selectedComponent = tabbedPane.getSelectedComponent();
      if (selectedComponent instanceof JComponent) {
        return getPreferredFocusedComponent((JComponent)selectedComponent);
      }
      return null;
    }

    if(_accept(component)) {
      return component;
    }

    for (Component ca : component.getComponents()) {
      if (!(ca instanceof JComponent)) {
        continue;
      }
      final JComponent c = getPreferredFocusedComponent((JComponent)ca);
      if (c != null) {
        return c;
      }
    }
    return null;
  }

  private static FocusTraversalPolicy getFocusTraversalPolicyAwtImpl(final JComponent component) {
    try {
      final Field field = Container.class.getDeclaredField(FOCUS_TRAVERSAL_POLICY_FIELD);
      field.setAccessible(true);
      return (FocusTraversalPolicy)field.get(component);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  protected final boolean accept(final Component aComponent) {
    if (aComponent instanceof JComponent) {
      return _accept((JComponent)aComponent);
    }
    return super.accept(aComponent);
  }

  private static boolean _accept(final JComponent component) {
    if (!component.isEnabled() || !component.isVisible() || !component.isFocusable()) {
      return false;
    }

    /** TODO[anton,vova] implement Policy in Editor component instead */
    if (component instanceof EditorComponentImpl) {
      return true;
    }

    if(component instanceof JTextComponent){
      return ((JTextComponent)component).isEditable();
    }

    return
      component instanceof AbstractButton ||
      component instanceof JList ||
      component instanceof JTree ||
      component instanceof JTable ||
      component instanceof JComboBox;
  }
}

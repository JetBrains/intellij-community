package com.intellij.ui;

import com.intellij.util.Function;

import javax.swing.*;

public class ListSpeedSearch extends SpeedSearchBase<JList> {
  private Function<Object, String> myElementTextDelegate;

  public ListSpeedSearch(JList list) {
    super(list);
  }

  public ListSpeedSearch(final JList component, final Function<Object, String> elementTextDelegate) {
    super(component);
    myElementTextDelegate = elementTextDelegate;
  }

  protected void selectElement(Object element, String selectedText) {
    ListScrollingUtil.selectItem(myComponent, element);
  }

  protected int getSelectedIndex() {
    return myComponent.getSelectedIndex();
  }

  protected Object[] getAllElements() {
    ListModel model = myComponent.getModel();
    if (model instanceof DefaultListModel){ // optimization
      return ((DefaultListModel)model).toArray();
    }
    else{
      Object[] elements = new Object[model.getSize()];
      for(int i = 0; i < elements.length; i++){
        elements[i] = model.getElementAt(i);
      }
      return elements;
    }
  }

  protected String getElementText(Object element) {
    if (myElementTextDelegate != null) {
      return myElementTextDelegate.fun(element);
    }
    return element.toString();
  }
}
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ArrayUtil;

/**
 * @author yole
 */
public class PlainTextFilter implements ElementFilter, InitializableFilter {
  protected String[] myValue;
  protected boolean myCaseInsensitiveFlag = false;

  public PlainTextFilter(final String value, final boolean insensitiveFlag) {
    myCaseInsensitiveFlag = insensitiveFlag;
    myValue = new String[1];
    myValue[0] = value;
  }

  public PlainTextFilter(final String... values) {
    myValue = values;
  }

  public PlainTextFilter(final String value1, final String value2) {
    myValue = new String[2];
    myValue[0] = value1;
    myValue[1] = value2;
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element != null) {
      for (final String value : myValue) {
        if (value == null) {
          return true;
        }
        final String elementText = getTextByElement(element);
        if (myCaseInsensitiveFlag) {
          if (value.equalsIgnoreCase(elementText)) return true;
        }
        else {
          if (value.equals(elementText)) return true;
        }
      }
    }

    return false;
  }

  public String toString(){
    String ret = "(";
    for(int i = 0; i < myValue.length; i++){
      ret += myValue[i];
      if(i < myValue.length - 1){
        ret += " | ";
      }
    }
    ret += ")";
    return ret;
  }

  public void init(Object[] fromGetter){
    try{
      myValue = new String[fromGetter.length];
      System.arraycopy(fromGetter, 0, myValue, 0, fromGetter.length);
    }
    catch(ClassCastException cce){
      myValue = ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  protected String getTextByElement(Object element){
    String elementValue = null;
    if(element instanceof PsiNamedElement){
      elementValue = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiElement) {
      elementValue = ((PsiElement) element).getText();
    }
    return elementValue;
  }
}

package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * This class represents an item of a lookup list.
 */
public final class LookupItem implements Comparable{
  public static final Object HIGHLIGHTED_ATTR = Key.create("highlighted");
  public static final Object TYPE_ATTR = Key.create("type");
  public static final Object ICON_ATTR = Key.create("icon");
  public static final Object TAIL_TEXT_ATTR = Key.create("tailText");
  public static final Object TAIL_TEXT_SMALL_ATTR = Key.create("tailTextSmall");
  public static final Object FORCE_SHOW_SIGNATURE_ATTR = Key.create("forceShowSignature");
  public static final Key<Object> FORCE_SHOW_FQN_ATTR = Key.create("forseFQNForClasses");

  public static final Object DO_NOT_AUTOCOMPLETE_ATTR = Key.create("DO_NOT_AUTOCOMPLETE_ATTR");
  public static final Object DO_AUTOCOMPLETE_ATTR = Key.create("DO_AUTOCOMPLETE_ATTR");
  public static final Object INSERT_HANDLER_ATTR = Key.create("INSERT_HANDLER_ATTR");

  public static final Object GENERATE_ANONYMOUS_BODY_ATTR = Key.create("GENERATE_ANONYMOUS_BODY_ATTR");
  public static final Object CONTAINING_CLASS_ATTR = Key.create("CONTAINING_CLASS_ATTR"); // used for dummy-constructors
  public static final Object BRACKETS_COUNT_ATTR = Key.create("BRACKETS_COUNT_ATTR");
  public static final Object OVERWRITE_ON_AUTOCOMPLETE_ATTR = Key.create("OVERWRITE_ON_AUTOCOMPLETE_ATTR");
  public static final Object NEW_OBJECT_ATTR = Key.create("NEW_OBJECT_ATTR");
  public static final Object DONT_CHECK_FOR_INNERS = Key.create("DONT_CHECK_FOR_INNERS");
  public static final Object FORCE_QUALIFY = Key.create("FORCE_QUALIFY");
  public static final Object SUBSTITUTOR = Key.create("SUBSTITUTOR");
  public static final Object TYPE = Key.create("TYPE");
  public static final Object INDICATE_ANONYMOUS = Key.create("INDICATE ANONYMOUS");

  private Object myObject;
  private String myLookupString;
  private Map<Object,Object> myAttributes = null;

  public LookupItem(Object o, @NotNull String lookupString){
    setObject(o);
    myLookupString = lookupString;
  }

  public void setObject(Object o) {
    if (o instanceof PsiElement){
      PsiElement element = (PsiElement)o;
      Project project = element.getProject();
      myObject = element.isPhysical() ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer((PsiElement)o) : element;
    }
    else{
      myObject = o;
    }
  }

  public boolean equals(Object o){
    if (o == this) return true;
    if (o instanceof LookupItem){
      LookupItem item = (LookupItem)o;
      return Comparing.equal(myObject, item.myObject)
             && Comparing.equal(myLookupString, item.myLookupString)
             && Comparing.equal(myAttributes, item.myAttributes);
    }
    return false;
  }

  public int hashCode() {
    return myLookupString.hashCode();
  }

  public String toString() {
    return getLookupString();
  }

  /**
   * Returns a data object.  This object is used e.g. for rendering the node.
   */
  public Object getObject() {
    if (myObject instanceof SmartPsiElementPointer){
      return ((SmartPsiElementPointer)myObject).getElement();
    }
    else{
      return myObject;
    }
  }

  /**
   * Returns a string which will be inserted to the editor when this item is
   * choosen.
   */
  public String getLookupString() {
    return myLookupString;
  }

  public void setLookupString(@NotNull String lookupString) {
    myLookupString = lookupString;
  }

  public Object getAttribute(Object key){
    if (myAttributes != null){
      return myAttributes.get(key);
    }
    else{
      return null;
    }
  }

  public void setAttribute(Object key, Object value){
    if (myAttributes == null){
      myAttributes = new HashMap<Object, Object>(5);
    }
    myAttributes.put(key, value);
  }

  public InsertHandler getInsertHandler(){
    return (InsertHandler)getAttribute(INSERT_HANDLER_ATTR);
  }

  public int getTailType(){
    final Integer tailType = (Integer) getAttribute(CompletionUtil.TAIL_TYPE_ATTR);
    if(tailType != null)
      return tailType.intValue();
    return -1;
  }

  public void setTailType(int type){
    setAttribute(CompletionUtil.TAIL_TYPE_ATTR, Integer.valueOf(type));
  }

  public int compareTo(Object o){
    if(o instanceof String){
      return getLookupString().compareTo(((String) o));
    }
    if(!(o instanceof LookupItem)){
      throw new RuntimeException("Trying to compare LookupItem with " + o.getClass() + "!!!");
    }
    return getLookupString().compareTo(((LookupItem)o).getLookupString());
  }
}

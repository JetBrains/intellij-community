package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.completion.simple.CompletionCharHandler;
import com.intellij.codeInsight.completion.simple.SimpleInsertHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.HashMap;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * This class represents an item of a lookup list.
 */
public class LookupItem<T> implements Comparable, LookupElement<T>{
  public static final Object HIGHLIGHTED_ATTR = Key.create("highlighted");
  public static final Object TYPE_ATTR = Key.create("type");
  public static final Object ICON_ATTR = Key.create("icon");
  public static final Object TYPE_TEXT_ATTR = Key.create("typeText");
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
  public static final Key<String> INSERT_TYPE_PARAMS = Key.create("INSERT_TYPE_PARAMS");

  public static final Key<Object> DONT_PREFER = Key.create("DONT_PREFER");
  public static final Key<int[]> WEIGHT = Key.create("WEIGHT");

  public static final Object CASE_INSENSITIVE = Key.create("CASE_INSENSITIVE");

  public static final Key<TailType> TAIL_TYPE_ATTR = Key.create("myTailType"); // one of constants defined in SimpleTailType interface

  private Object myObject;
  private String myLookupString;
  private double myPriority;
  private int myGrouping;
  private Map<Object,Object> myAttributes = null;
  public static final LookupItem[] EMPTY_ARRAY = new LookupItem[0];
  public static final CompletionCharHandler DEFAULT_COMPLETION_CHAR_HANDLER = new CompletionCharHandler() {
    public TailType handleCompletionChar(@NotNull final Editor editor, @NotNull final LookupElement lookupElement, final char completionChar) {
      if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
        final int offset = editor.getCaretModel().getOffset();
        final Document document = editor.getDocument();
        final CharSequence sequence = document.getCharsSequence();
        int i = offset;
        while (i < sequence.length() && Character.isJavaIdentifierPart(sequence.charAt(i))) i++;
        document.deleteString(offset, i);
        PsiDocumentManager.getInstance(editor.getProject()).commitDocument(editor.getDocument());
      }
      return DefaultInsertHandler.getTailType(completionChar, (LookupItem)lookupElement);
    }
  };
  @NotNull private CompletionCharHandler<T> myCompletionCharHandler = DEFAULT_COMPLETION_CHAR_HANDLER;
  private final Set<String> myAllLookupStrings = new THashSet<String>();
  private String myPresentable;

  public LookupItem(T o, @NotNull @NonNls String lookupString){
    setObject(o);
    setLookupString(lookupString);
  }

  public void setObject(@NotNull T o) {
    if (o instanceof PsiElement){
      PsiElement element = (PsiElement)o;
      Project project = element.getProject();
      myObject = element.isPhysical() ? SmartPointerManager.getInstance(project).createSmartPsiElementPointer((PsiElement)o) : element;
    }
    else{
      myObject = o;
    }

    if (o instanceof LookupValueWithPriority) {
      setPriority(((LookupValueWithPriority)o).getPriority());
    }
  }

  public boolean equals(Object o){
    if (o == this) return true;
    if (o instanceof LookupItem){
      LookupItem item = (LookupItem)o;
      return Comparing.equal(myObject, item.myObject)
             && Comparing.equal(myLookupString, item.myLookupString)
             && Comparing.equal(myAllLookupStrings, item.myAllLookupStrings)
             && Comparing.equal(myAttributes, item.myAttributes);
    }
    return false;
  }

  public int hashCode() {
    return myAllLookupStrings.hashCode();
  }

  public String toString() {
    return getLookupString();
  }

  /**
   * Returns a data object.  This object is used e.g. for rendering the node.
   */
  @NotNull
  public T getObject() {
    if (myObject instanceof SmartPsiElementPointer){
      final T t = (T)((SmartPsiElementPointer)myObject).getElement();
      assert t != null : "Pointer = " + myObject + "; lookupStrings = " + myAllLookupStrings;
      return t;
    }
    else{
      return (T)myObject;
    }
  }

  /**
   * Returns a string which will be inserted to the editor when this item is
   * choosen.
   */
  @NotNull
  public String getLookupString() {
    return myLookupString;
  }

  public void setLookupString(@NotNull String lookupString) {
    if (myAllLookupStrings.contains("")) myAllLookupStrings.remove("");
    myLookupString = lookupString;
    myAllLookupStrings.add(lookupString);
  }

  public Object getAttribute(Object key){
    if (myAttributes != null){
      return myAttributes.get(key);
    }
    else{
      return null;
    }
  }

  public <T> T getAttribute(Key<T> key) {
    if (myAttributes != null){
      //noinspection unchecked
      return (T)myAttributes.get(key);
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

  public <T> void setAttribute(Key<T> key, T value){
    if (myAttributes == null){
      myAttributes = new HashMap<Object, Object>(5);
    }
    myAttributes.put(key, value);
  }

  public InsertHandler getInsertHandler(){
    return (InsertHandler)getAttribute(INSERT_HANDLER_ATTR);
  }

  @NotNull
  public TailType getTailType(){
    final TailType tailType = getAttribute(TAIL_TYPE_ATTR);
    return tailType != null ? tailType : TailType.UNKNOWN;
  }

  @NotNull
  public LookupItem<T> setTailType(@NotNull TailType type) {
    setAttribute(TAIL_TYPE_ATTR, type);
    return this;
  }

  public int compareTo(Object o){
    if(o instanceof String){
      return getLookupString().compareTo((String)o);
    }
    if(!(o instanceof LookupItem)){
      throw new RuntimeException("Trying to compare LookupItem with " + o.getClass() + "!!!");
    }
    return getLookupString().compareTo(((LookupItem)o).getLookupString());
  }

  public LookupItem<T> setInsertHandler(@NotNull final SimpleInsertHandler handler) {
    setAttribute(INSERT_HANDLER_ATTR, new MyInsertHandler(handler));
    return this;
  }

  public LookupItem<T> setCompletionCharHandler(@NotNull final CompletionCharHandler<T> completionCharHandler) {
    myCompletionCharHandler = completionCharHandler;
    return this;
  }

  public LookupItem<T> setBold() {
    setAttribute(HIGHLIGHTED_ATTR, "");
    return this;
  }

  @NotNull
  public LookupItem<T> setIcon(Icon icon) {
    setAttribute(ICON_ATTR, icon);
    return this;
  }

  @NotNull
  public LookupItem<T> setPriority(double priority) {
    myPriority = priority;
    return this;
  }

  @NotNull
  public LookupElement<T> setGrouping(final int grouping) {
    myGrouping = grouping;
    return this;
  }

  public final double getPriority() {
    return myPriority;
  }

  public final int getGrouping() {
    return myGrouping;
  }

  @NotNull
  public LookupElement<T> setPresentableText(@NotNull final String displayText) {
    myPresentable = displayText;
    return this;
  }

  @Nullable
  public String getPresentableText() {
    return myPresentable;
  }

  @NotNull
  public LookupItem setTypeText(final String text) {
    setAttribute(TYPE_TEXT_ATTR, text);
    return this;
  }

  @NotNull
  public LookupItem<T> setCaseSensitive(final boolean caseSensitive) {
    setAttribute(CASE_INSENSITIVE, !caseSensitive);
    return this;
  }

  public LookupItem<T> addLookupStrings(@NonNls final String... additionalLookupStrings) {
    myAllLookupStrings.addAll(Arrays.asList(additionalLookupStrings));
    return this;
  }

  public Set<String> getAllLookupStrings() {
    return myAllLookupStrings;
  }

  public void copyAttributes(final LookupItem item) {
    myAttributes.putAll(item.myAttributes);
  }

  private class MyInsertHandler implements InsertHandler {
    private final SimpleInsertHandler myHandler;

    public MyInsertHandler(final SimpleInsertHandler handler) {
      myHandler = handler;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final LookupItem.MyInsertHandler that = (LookupItem.MyInsertHandler)o;

      if (!myHandler.equals(that.myHandler)) return false;

      return true;
    }

    public int hashCode() {
      return myHandler.hashCode();
    }

    public void handleInsert(final CompletionContext context,
                             final int startOffset, final LookupData data, final LookupItem item,
                             final boolean signatureSelected, final char completionChar) {
      final Editor editor = context.editor;
      TailType tailType = myCompletionCharHandler.handleCompletionChar(editor, item, completionChar);
      if (tailType == null) {
        tailType = DEFAULT_COMPLETION_CHAR_HANDLER.handleCompletionChar(editor, item, completionChar);
      }
      assert tailType != null;
      final int tailOffset;
      try {
        tailOffset = myHandler.handleInsert(editor, startOffset, LookupItem.this, data.items, tailType);
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
      tailType.processTail(editor, tailOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }
}

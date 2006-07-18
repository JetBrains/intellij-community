package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class LookupManagerImpl extends LookupManager implements ProjectComponent {
  private final Project myProject;

  protected Lookup myActiveLookup = null;
  protected Editor myActiveLookupEditor = null;
  private PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  protected static final Comparator<LookupItem> COMPARATOR = new Comparator<LookupItem>(){
    public int compare(LookupItem o1, LookupItem o2){
      int priority = o1.getObject() instanceof LookupValueWithPriority ?
                     ((LookupValueWithPriority)o1.getObject()).getPriority():
                     LookupValueWithPriority.NORMAL;
      
      int priority2 = o2.getObject() instanceof LookupValueWithPriority ?
                     ((LookupValueWithPriority)o2.getObject()).getPriority():
                     LookupValueWithPriority.NORMAL;
      if (priority != priority2) {
        return priority2 - priority;
      }
      return o1.getLookupString().compareToIgnoreCase(o2.getLookupString());
    }
  };
  private boolean myIsDisposed;
  private EditorFactoryAdapter myEditorFactoryListener;

  public LookupManagerImpl(Project project, EditorFactory editorFactory) {
    myProject = project;

    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorReleased(EditorFactoryEvent event) {
        if (event.getEditor() == myActiveLookupEditor){
          hideActiveLookup();
        }
      }
    };
    editorFactory.addEditorFactoryListener(myEditorFactoryListener);
  }

  @NotNull
  public String getComponentName(){
    return "LookupManager";
  }

  public void initComponent() { }

  public void disposeComponent(){
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    myIsDisposed = true;
  }

  public void projectOpened(){
  }

  public void projectClosed(){
  }

  public Lookup showLookup(
      final Editor editor,
      LookupItem[] items,
      String prefix,
      LookupItemPreferencePolicy itemPreferencePolicy,
      CharFilter filter
      ) {
    hideActiveLookup();

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    items = items.clone();
    if (!settings.SHOW_SIGNATURES_IN_LOOKUPS){
      items = filterEqualSignatures(items);
    }

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

    if (sortItems(psiFile, items)) {
      Arrays.sort(items, COMPARATOR);
    }

    final Alarm alarm = new Alarm();
    final Runnable request = new Runnable(){
      public void run() {
        JavaDocManager.getInstance(myProject).showJavaDocInfo(editor, psiFile, false);
      }
    };
    if (settings.AUTO_POPUP_JAVADOC_INFO){
      alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
    }

    final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    if (daemonCodeAnalyzer != null) {
      daemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    }
    myActiveLookup = new LookupImpl(myProject, editor, items, prefix, itemPreferencePolicy, filter);
    myActiveLookupEditor = editor;
    ((LookupImpl)myActiveLookup).show();
    myActiveLookup.addLookupListener(
      new LookupAdapter(){
        public void itemSelected(LookupEvent event) {
          dispose();
        }

        public void lookupCanceled(LookupEvent event) {
          dispose();
        }

        public void currentItemChanged(LookupEvent event) {
          alarm.cancelAllRequests();
          if (settings.AUTO_POPUP_JAVADOC_INFO){
            alarm.addRequest(request, settings.JAVADOC_INFO_DELAY);
          }
        }

        private void dispose(){
          if (myActiveLookup == null) return;
          alarm.cancelAllRequests();
          if (daemonCodeAnalyzer != null) {
            daemonCodeAnalyzer.setUpdateByTimerEnabled(true);
          }
          myActiveLookup.removeLookupListener(this);
          Lookup lookup = myActiveLookup;
          myActiveLookup = null;
          myActiveLookupEditor = null;
          myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, lookup, myActiveLookup);
        }
      }
    );
    myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, null, myActiveLookup);
    return myActiveLookup;
  }

  public void hideActiveLookup() {
    if (myActiveLookup != null){
      ((LookupImpl)myActiveLookup).hide();
      Lookup lookup = myActiveLookup;
      myActiveLookup = null;
      myActiveLookupEditor = null;
      myPropertyChangeSupport.firePropertyChange(PROP_ACTIVE_LOOKUP, lookup, myActiveLookup);
    }
  }

  private static boolean sortItems(PsiFile containingFile, LookupItem[] items) {
    if (!(containingFile instanceof XmlFile)) return true;

    for (LookupItem item : items) {
      final Object object = item.getObject();
      
      if (object instanceof PsiElement ||
          object instanceof LookupValueWithPriority) {
        return true;
      }
    }

    return CodeInsightSettings.getInstance().SORT_XML_LOOKUP_ITEMS;
  }

  public Lookup getActiveLookup() {
    return myActiveLookup;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  public PsiElement[] getAllElementsForItem(LookupItem item) {
    PsiMethod[] allMethods = (PsiMethod[])item.getAttribute(LookupImpl.ALL_METHODS_ATTRIBUTE);
    if (allMethods != null){
      return allMethods;
    }
    else{
      if (item.getObject() instanceof PsiElement){
        return new PsiElement[]{(PsiElement)item.getObject()};
      }
      else{
        return null;
      }
    }
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  protected static LookupItem[] filterEqualSignatures(LookupItem[] items) {
    ArrayList<LookupItem> array = new ArrayList<LookupItem>();
    HashMap<String, LookupItem> methodNameToItem = new HashMap<String, LookupItem>();
    for (LookupItem item : items) {
      if (item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null) {
        array.add(item);
        continue;
      }
      Object o = item.getObject();
      if (o instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)o;
        String name = method.getName();
        LookupItem item1 = methodNameToItem.get(name);
        if (item1 != null) {
          ArrayList<PsiMethod> allMethods = (ArrayList<PsiMethod>)item1.getAttribute(LookupImpl.ALL_METHODS_ATTRIBUTE);
          allMethods.add(method);
          continue;
        }
        else {
          methodNameToItem.put(name, item);
          ArrayList<PsiMethod> allMethods = new ArrayList<PsiMethod>();
          allMethods.add(method);
          item.setAttribute(LookupImpl.ALL_METHODS_ATTRIBUTE, allMethods);
        }
      }
      array.add(item);
    }
    items = array.toArray(new LookupItem[array.size()]);
    for (LookupItem item : items) {
      ArrayList<PsiMethod> allMethods = (ArrayList<PsiMethod>)item.getAttribute(LookupImpl.ALL_METHODS_ATTRIBUTE);
      if (allMethods != null) {
        item.setAttribute(LookupImpl.ALL_METHODS_ATTRIBUTE, allMethods.toArray(new PsiMethod[allMethods.size()]));
      }
    }
    return items;
  }
}
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.CompletionPreferencePolicy;
import com.intellij.codeInsight.completion.actions.SmartCodeCompletionAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiProximityComparator;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
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

  private boolean myIsDisposed;
  private EditorFactoryAdapter myEditorFactoryListener;

  public LookupManagerImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public String getComponentName(){
    return "LookupManager";
  }

  public void initComponent() { }

  public void disposeComponent(){
  }

  public void projectOpened(){
    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorReleased(EditorFactoryEvent event) {
        if (event.getEditor() == myActiveLookupEditor){
          hideActiveLookup();
        }
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
  }

  public void projectClosed(){
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
    myIsDisposed = true;
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
    PsiElement context = psiFile;
    if (psiFile != null) {
      final PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        context = element;
      }
    }

    sortItems(context, items, itemPreferencePolicy);

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
          alarm.cancelAllRequests();
          if (daemonCodeAnalyzer != null) {
            daemonCodeAnalyzer.setUpdateByTimerEnabled(true);
          }
          if (myActiveLookup == null) return;
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

  private static boolean hasFewAbstractMethods(final PsiClass psiClass) {
    int count = 0;
    for (final PsiMethod method : psiClass.getAllMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        count++;
        if (count > 2) return false;
      }
    }
    return count != 0;
  }
  

  protected void sortItems(PsiElement context, LookupItem[] items, final LookupItemPreferencePolicy itemPreferencePolicy) {
    if (context == null || shouldSortItems(context.getContainingFile(), items)) {
      final PsiProximityComparator proximityComparator = new PsiProximityComparator(context, myProject);
      if (isUseNewSorting()) {
        if (itemPreferencePolicy instanceof CompletionPreferencePolicy) {
          final ExpectedTypeInfo[] expectedInfos = ((CompletionPreferencePolicy)itemPreferencePolicy).getExpectedInfos();
          if (expectedInfos != null) {
            final THashSet<PsiClass> set = getFirstClasses(expectedInfos);
            for (final LookupItem item : items) {
              final Object o = item.getObject();
              if (set.contains(o) && !hasFewAbstractMethods((PsiClass)o)) {
                item.setAttribute(LookupItem.DONT_PREFER, "");
              }
            }
          }
        }
      }

      final Comparator<? super LookupItem> comparator = new Comparator<LookupItem>() {
        public int compare(LookupItem o1, LookupItem o2) {
          double priority1 = o1.getPriority();
          double priority2 = o2.getPriority();
          if (priority1 > priority2) return -1;
          if (priority2 > priority1) return 1;

          int grouping1 = o1.getGrouping();
          int grouping2 = o2.getGrouping();
          if (grouping1 > grouping2) return -1;
          if (grouping2 > grouping1) return 1;

          int stringCompare = o1.getLookupString().compareToIgnoreCase(o2.getLookupString());
          return stringCompare != 0 ? stringCompare : proximityComparator.compare(o1.getObject(), o2.getObject());
        }


      };
      Arrays.sort(items, comparator);
    }
  }

  public static boolean isUseNewSorting() {
    return SmartCodeCompletionAction.isDoingSmartCodeCompleteAction() && "true".equals(System.getProperty("sort.lookup.items.by.proximity"));
  }

  protected boolean shouldSortItems(final PsiFile containingFile, final LookupItem[] items) {
    if (!(containingFile instanceof XmlFile)) return true;

    for (LookupItem item : items) {
      final Object object = item.getObject();

      if (object instanceof PsiElement ||
          object instanceof LookupValueWithPriority || item.getPriority() != 0) {
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

  public static THashSet<PsiClass> getFirstClasses(final ExpectedTypeInfo[] expectedInfos) {
    final THashSet<PsiClass> set = new THashSet<PsiClass>();
    for (final ExpectedTypeInfo info : expectedInfos) {
      addFirstPsiType(set, info.getType());
      addFirstPsiType(set, info.getDefaultType());
    }
    return set;
  }

  private static void addFirstPsiType(final THashSet<PsiClass> set, final PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null) {
        set.add(psiClass);
      }
    }
  }

}
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author max
 */
public class DocumentMarkupModelManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.DocumentMarkupModelManager");

  private final WeakHashMap<DocumentImpl,String> myDocumentSet = new WeakHashMap<DocumentImpl, String>();
  private final Project myProject;
  private boolean myIsDisposed = false;

  public static DocumentMarkupModelManager getInstance(Project project) {
    return project.getComponent(DocumentMarkupModelManager.class);
  }

  public DocumentMarkupModelManager(Project project) {
    myProject = project;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
    cleanup();
  }

  public void registerDocument(DocumentImpl doc) {
    LOG.assertTrue(!myIsDisposed);
    myDocumentSet.put(doc, "");
  }

  @NotNull
  public String getComponentName() {
    return "DocumentMarkupModelManager";
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public void initComponent() { }

  public void disposeComponent() {
    cleanup();
  }

  private void cleanup() {
    if (!myIsDisposed) {
      myIsDisposed = true;
      Set<DocumentImpl> docs = myDocumentSet.keySet();
      for (DocumentImpl doc : docs) {
        doc.removeMarkupModel(myProject);
      }
    }
  }
}

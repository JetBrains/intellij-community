package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class RefModuleImpl extends RefEntityImpl implements RefModule {
  private final Module myModule;

  protected RefModuleImpl(Module module, final RefManager manager) {
    super(module.getName(), manager);
    myModule = module;
    ((RefProjectImpl)manager.getRefProject()).add(this);
  }

  public void add(RefEntity child) {
    if (myChildren == null) {
       myChildren = new ArrayList<RefEntity>();
    }
    myChildren.add(child);

    if (child.getOwner() == null) {
      ((RefEntityImpl)child).setOwner(this);
    }
  }

  protected void removeChild(RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
    }
  }

  public void accept(final RefVisitor refVisitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        refVisitor.visitModule(RefModuleImpl.this);
      }
    });
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  public boolean isValid() {
    return myModule != null && !myModule.isDisposed();
  }

  public Icon getIcon(final boolean expanded) {
    return getModule().getModuleType().getNodeIcon(expanded);
  }

  @Nullable
  public static RefEntity moduleFromName(final RefManager manager, final String name) {
    return manager.getRefModule(ModuleManager.getInstance(manager.getProject()).findModuleByName(name));
  }
}

package org.jetbrains.jps.model.module.impl;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsModuleReference;

/**
 * @author nik
 */
public class JpsModuleDependencyImpl extends JpsDependencyElementBase<JpsModuleDependencyImpl> implements JpsModuleDependency {
  private static final JpsElementChildRole<JpsModuleReference>
    MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("module reference");

  private volatile Ref<JpsModule> myCachedModule = null;

  public JpsModuleDependencyImpl(final JpsModuleReference moduleReference) {
    super();
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
  }

  public JpsModuleDependencyImpl(JpsModuleDependencyImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }

  @Override
  public JpsModule getModule() {
    Ref<JpsModule> moduleRef = myCachedModule;
    if (moduleRef == null) {
      moduleRef = new Ref<JpsModule>(getModuleReference().resolve());
      myCachedModule = moduleRef;
    }
    return moduleRef.get();
  }

  @NotNull
  @Override
  public JpsModuleDependencyImpl createCopy() {
    return new JpsModuleDependencyImpl(this);
  }

  @Override
  public String toString() {
    return "module dep [" + getModuleReference() + "]";
  }
}

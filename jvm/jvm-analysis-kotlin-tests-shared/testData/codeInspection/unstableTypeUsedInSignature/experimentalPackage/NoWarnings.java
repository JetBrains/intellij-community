package experimentalPackage;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
class ExperimentalClass { }

// No warnings because the 'experimentalPackage', to which this class belongs, is also experimental.

class NoWarnings {
  public ExperimentalClass field;

  public void methodWithParam(ExperimentalClass param) {}

  public ExperimentalClass methodWithReturnType() { return null; }


  public ClassInExperimentalPackage fieldWithSamePackageType;

  public void methodWithSamePackageParam(ClassInExperimentalPackage param) {}

  public ClassInExperimentalPackage methodWithSamePackageReturnType() { return null; }
}
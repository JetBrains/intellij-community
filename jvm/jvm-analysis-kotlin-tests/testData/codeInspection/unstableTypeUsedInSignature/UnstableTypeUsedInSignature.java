package test;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import experimentalPackage.ClassInExperimentalPackage;

public class UnstableTypeUsedInSignature {

  public ExperimentalClass <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'test.ExperimentalClass'">field</warning>;

  public ExperimentalClass[] <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'test.ExperimentalClass'">arrayField</warning>;

  public List<ExperimentalClass> <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'test.ExperimentalClass'">listField</warning>;

  public void <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalParam</warning>(ExperimentalClass param) {
  }

  public void <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalParamArray</warning>(ExperimentalClass[] param) {
  }

  public void <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalParamArray</warning>(List<ExperimentalClass> param) {
  }

  public ExperimentalClass <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalReturnType</warning>() {
    return null;
  }

  public ExperimentalClass[] <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalReturnTypeArray</warning>() {
    return null;
  }

  public List<ExperimentalClass> <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalReturnTypeList</warning>() {
    return null;
  }

  public <T extends ExperimentalClass> T <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithUnstableTypeParameter</warning>() {
    return null;
  }

  public void <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithUnstableTypeParameterExtendsWildcard</warning>(List<? extends ExperimentalClass> list) {
  }

  public void <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithUnstableTypeParameterSuperWildcard</warning>(List<? super ExperimentalClass> list) {
  }

  public ClassInExperimentalPackage <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'experimentalPackage.ClassInExperimentalPackage'">fieldWithTypeFromExperimentalPackage</warning>;

  public void <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'experimentalPackage.ClassInExperimentalPackage'">methodWithParamTypeFromExperimentalPackage</warning>(ClassInExperimentalPackage param) { }

  public ClassInExperimentalPackage <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'experimentalPackage.ClassInExperimentalPackage'">methodWithReturnTypeFromExperimentalPackage</warning>() { return null; }
}
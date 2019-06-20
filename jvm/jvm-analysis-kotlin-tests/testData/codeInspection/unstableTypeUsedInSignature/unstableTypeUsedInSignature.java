package test;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Experimental
class ExperimentalClass { }

class Warnings {

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
}

class <warning descr="Class must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its declaration references unstable type 'test.ExperimentalClass'">WarningTypeParameter</warning><T extends ExperimentalClass> {
}

// No warnings should be produced because the declaring class is experimental itself.

@ApiStatus.Experimental
class NoWarningsClassLevel {

  public ExperimentalClass field;

  public void methodWithExperimentalParam(ExperimentalClass param) {
  }

  public ExperimentalClass methodWithExperimentalReturnType() {
    return null;
  }
}

// No warnings should be produced because methods and fields are already marked with @ApiStatus.Experimental annotation.

class NoWarnings {

  @ApiStatus.Experimental
  public ExperimentalClass field;

  @ApiStatus.Experimental
  public void methodWithExperimentalParam(ExperimentalClass param) {
  }

  @ApiStatus.Experimental
  public ExperimentalClass methodWithExperimentalReturnType() {
    return null;
  }
}
package test;

import org.jetbrains.annotations.ApiStatus;

public class NoWarningsInaccessible {
  private ExperimentalClass privateField;
  private void privateMethodWithParam(ExperimentalClass param) { }
  private ExperimentalClass privateMethodWithReturnType() { return null; }

  ExperimentalClass packageLocalField;
  void packageLocalMethodWithParam(ExperimentalClass param) { }
  ExperimentalClass packageLocalMethodWithReturnType() { return null; }

  public void anonymousClassNoWarnings() {
    new Object() {
      public ExperimentalClass anonymousField;

      public void anonymousMethodWithParamType(ExperimentalClass param) {
      }

      public ExperimentalClass anonymousMethodWithReturnType() {
        return null;
      }
    };
  }
}

class NoWarningsPackageLocalClass<T extends ExperimentalClass> {
}
import org.checkerframework.checker.tainting.qual.Untainted;


class A {

  void test(@Untainted String clean, String dirty) {
    sink(clean);
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>);  //warn
    String cleanFromIf;
    if (1 == 1) {
      cleanFromIf = clean;
    } else {
      cleanFromIf = "1";
    }
    sink(cleanFromIf);
    String cleanAfterIf;
    if (1 == 1) {
      cleanAfterIf = clean;
    } else {
      cleanAfterIf = dirty;
    }
    sink(cleanAfterIf);
    sink(<warning descr="Unknown string is used as safe parameter">1 == 1 ? dirty : clean</warning>); //warn
    String cleanFromSwitch = switch (dirty) {
      default -> "1";
    };
    sink(cleanFromSwitch);
    String dirtyFromSwitch = switch (dirty) {
      default -> dirty;
    };
    sink(<warning descr="Unknown string is used as safe parameter">dirtyFromSwitch</warning>); //warn
    String cleanFromSwitchStatement;
    switch (dirty) {
      case "1":
        cleanFromSwitchStatement = "2";
        break;
      default:
        cleanFromSwitchStatement = "3";
    }
    sink(cleanFromSwitchStatement);
    String dirtyFromSwitchStatement;
    switch (dirty) {
      case "1":
        dirtyFromSwitchStatement = "2";
        break;
      default:
        dirtyFromSwitchStatement = dirty;
    }
    sink(<warning descr="Unknown string is used as safe parameter">dirtyFromSwitchStatement</warning>); //warn
    String cleanLoop = "1";
    while (true) {
      cleanLoop = "2";
      break;
    }
    sink(cleanLoop);
    String dirtyLoop = "1";
    while (true) {
      dirtyLoop = dirty;
      break;
    }
    sink(<warning descr="Unknown string is used as safe parameter">dirtyLoop</warning>); //warn
  }

  void sink(@Untainted String s) {
  }
}

import java.util.*;
class Test {
  private final Map<String, String> myModuleToOutput = new HashMap<String, String>();

  void foo() {
    if (myModuleToOutput.containsKey(null)) myModuleToOutput.put("", "");
  }
}
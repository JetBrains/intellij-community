import java.util.*;

class IdempotentLoopBody {
  String getUniqueName(String baseName, Set<String> names) {
    int index = 1;
    String name = baseName;
    <warning descr="Idempotent loop body">while</warning>(names.contains(name)) {
      name = baseName + index;
    }
    return name;
  }

  String getUniqueNameComplex(String baseName, Set<String> names) {
    int index = 1;
    String name = baseName;
    <warning descr="Idempotent loop body">while</warning> (names.contains(name)) {
      String suffix = String.valueOf(index);
      if (suffix.length() == 1) {
        suffix = "0" + suffix;
      }
      name = baseName + suffix;
    }
    return name;
  }

  String getUniqueNameCorrect(String baseName, Set<String> names) {
    int index = 1;
    String name = baseName;
    while(names.contains(name)) {
      name = baseName + (index++);
    }
    return name;
  }

  String getUniqueNameFor(String baseName, Set<String> names) {
    int index = 1;
    String name;
    <warning descr="Idempotent loop body">for</warning>(name = baseName; names.contains(name); name = baseName + index);
    return name;
  }

  String leftPad(String val, int desiredLength) {
    String result = val;
    <warning descr="Idempotent loop body">while</warning>(result.length() < desiredLength) {
      result = " " + val;
    }
    return result;
  }

  String leftPadCorrect(String val, int desiredLength) {
    String result = val;
    while(result.length() < desiredLength) {
      result = " " + result;
    }
    return result;
  }
  
  volatile int x;
  
  int testVolatile() {
    while(true) {
      int localX = x;
      if (localX > 0) return localX;
      if (localX % 2 == 0) return localX / 2;
    }
  }
}
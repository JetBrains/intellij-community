// "Transform body to single exit-point form" "true-preview"
class X {
  private static Integer testMultipleReturnPoints() {
      Integer result = null;
      try {
      List<Integer> integers = new ArrayList<>();

      for (Integer integer : integers) {
        if (integer == 1) {
            result = integer;
            break;
        }
        else if (integer == 2) {
            result = integer;
            break;
        }
      }

      }
    finally {

    }
      return result;
  }
}
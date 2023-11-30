class ConvertToIf {

  void test(boolean b, int i) {
    Class<?> c = (b ?
                  switch (i) {<caret>
                    case 1 -> true;
                    default -> 0;
                  } : 1).getClass();

    System.out.println(c);
  }
}

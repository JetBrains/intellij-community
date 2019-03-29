class Test {
  void foo(String[] ss) {
    Integer[] levels = new Integer[]{Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3),};
    Integer[] nextWinNumber = new Integer[6];
      NewMethodResult x = newMethod(levels, nextWinNumber);
      for (Integer level : levels) {
      Integer nextWinNum = nextWinNumber[level - 1];
    }
  }//ins and outs
//in: PsiLocalVariable:levels
//in: PsiLocalVariable:nextWinNumber
//exit: SEQUENTIAL PsiMethod:foo

    NewMethodResult newMethod(Integer[] levels, Integer[] nextWinNumber) {
        for (Integer level : levels) {
          Integer nextWinNum = nextWinNumber[level - 1];
        }
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
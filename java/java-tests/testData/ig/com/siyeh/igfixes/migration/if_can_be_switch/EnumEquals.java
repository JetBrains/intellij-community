class X {

  void f(DetailsEnum detailsEnum) {
    <caret>if (detailsEnum.equals(DetailsEnum.EDIT)) {
    }
    else if (detailsEnum.equals(DetailsEnum.BASE)) {
    }
    else if (detailsEnum.equals(DetailsEnum.FULL)) {
    }
    else {
      throw new RuntimeException();
    }
  }

  enum DetailsEnum {
    EDIT, BASE, FULL
  }
}
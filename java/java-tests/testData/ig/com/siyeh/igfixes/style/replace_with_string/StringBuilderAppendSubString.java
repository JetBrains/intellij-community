class StringBuilderAppend {
  String enclose(String str, int beginIndex, int endIndex) {
    return new StringBui<caret>lder(/*1*/)/*2*/./*3*/append(/*4*/'L'/*5*/)/*6*/./*7*/append(/*8*/str/*9*/, /*10*/beginIndex/*11*/, /*12*/endIndex/*13*/)/*14*/./*15*/append/*16*/(/*17*/';'/*18*/)/*19*/./*20*/toString(/*21*/);
  }
}
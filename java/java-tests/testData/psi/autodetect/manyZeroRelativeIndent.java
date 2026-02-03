class Test {

  public void test() {
    // boolean function-available(string)
    addFunction(decls, new FunctionImpl("function-available", XPathType.BOOLEAN,
                new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    // node-set current()
    addFunction(decls, new FunctionImpl("current", XPathType.NODESET));

    // EXSLT (http://www.exslt.org) extensions supported by Xalan & Saxon
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("date", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("date-time", XPathType.STRING));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-abbreviation", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-in-month", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-in-week", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-in-year", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-name", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-of-week-in-month", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("hour-in-day", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("leap-year", XPathType.BOOLEAN, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("minute-in-hour", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("month-abbreviation", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("month-in-year", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("month-name", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("second-in-minute", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("time", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("week-in-year", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("year", XPathType.NUMBER, optional_string));
  }
}

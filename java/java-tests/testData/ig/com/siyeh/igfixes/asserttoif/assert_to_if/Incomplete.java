package com.siyeh.ipp.asserttoif.assert_to_if;

public class Incomplete {

  void x(Object o) {
    assert<caret> o != null :
  }
}

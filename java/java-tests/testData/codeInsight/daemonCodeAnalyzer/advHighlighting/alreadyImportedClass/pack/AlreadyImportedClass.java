package pack;

import java.util.Date;
import pack.my;

class <error descr="'Date' is already defined in this compilation unit">Date</error> {
}

class my {
  class Date extends java.util.Date {
  }
}
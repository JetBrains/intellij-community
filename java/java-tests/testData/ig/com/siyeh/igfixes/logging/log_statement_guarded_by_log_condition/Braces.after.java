package com.siyeh.igfixes.logging.log_statement_guarded_by_log_condition;


import java.util.logging.Logger;

class Braces {

  private static final Logger LOG = Logger.getLogger("log");

  void m(boolean b) {
    if (b) {
        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine("time: " + System.currentTimeMillis());
        }
    }
    else
      System.out.println();
  }
}
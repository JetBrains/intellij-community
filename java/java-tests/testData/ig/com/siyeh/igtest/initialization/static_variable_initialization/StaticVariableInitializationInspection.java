package com.siyeh.igtest.initialization.static_variable_initialization;

import org.<error descr="Cannot resolve symbol 'xml'">xml</error>.sax.SAXException;

import <error descr="Cannot resolve symbol 'javax'">javax</error>.xml.parsers.SAXParserFactory;
import <error descr="Cannot resolve symbol 'javax'">javax</error>.xml.parsers.ParserConfigurationException;
import <error descr="Cannot resolve symbol 'javax'">javax</error>.xml.parsers.SAXParser;

public class StaticVariableInitializationInspection
{
    public static int <warning descr="Static field 's_fooBar' may not be initialized during class initialization">s_fooBar</warning>;        // may not be initialized
    public static int s_fooBaz = 1;
    public static int s_fooBarangus;
    public static int s_fooBazongas;

    static
    {
        s_fooBarangus = 2;
        staticCall();
    }

    private static void staticCall()
    {
        s_fooBazongas = 3;
    }

    private static final <error descr="Cannot resolve symbol 'SAXParser'">SAXParser</error> SAX_PARSER;

    static {
        try {
            SAX_PARSER = <error descr="Cannot resolve symbol 'SAXParserFactory'">SAXParserFactory</error>.newInstance().newSAXParser();
        } catch (<error descr="Cannot resolve symbol 'ParserConfigurationException'">ParserConfigurationException</error> e) {
            throw new RuntimeException<error descr="Cannot resolve constructor 'RuntimeException(ParserConfigurationException)'">(e)</error>;
        } catch (<error descr="Cannot resolve symbol 'SAXException'">SAXException</error> e) {
            throw new RuntimeException<error descr="Cannot resolve constructor 'RuntimeException(SAXException)'">(e)</error>;
        }
    }
}
class FinalField {
  public static final Object o;

  static {
    System.out.println("o = " + <error descr="Variable 'o' might not have been initialized">o</error>);
    o = null;
  }
}

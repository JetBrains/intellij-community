package com.siyeh.igtest.bugs;

import java.util.Properties;

public class UseOfPropertiesAsHashtable {
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.<warning descr="Call to 'Hashtable.put()' on properties object"><caret>put</warning>("foo", "bar");
        properties.<warning descr="Call to 'Hashtable.put()' on properties object">put</warning>("x", 1);
        properties.<warning descr="Call to 'Hashtable.putAll()' on properties object">putAll</warning>(null);
        String value = (String)properties.<warning descr="Call to 'Hashtable.get()' on properties object">get</warning>("foo");
        Long l = (Long)properties.<warning descr="Call to 'Hashtable.get()' on properties object">get</warning>("x");
    }
}

/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * This is a dummy test bean for testing the toString() plugin. Has a long list of fields.
 */
@SuppressWarnings({"rawtypes", "unused", "UseOfObsoleteCollectionType", "RedundantSuppression"})
public class DummyCompleteTestBean implements Serializable {

    // Constants
    private static final String CONSTANT_PRIVATE = "CONSTANT_PRIVATE";
    public static final String CONSTANT_PUBLIC = "CONSTANT_PUBLIC";
    private static final Object LOCK = new Object();

    // Singleton
    private static DummyCompleteTestBean singleton;

    // Transient
    private transient Object doNotStreamMe;

    // Primitives
    private byte _byte;
    private boolean _boolean;
    private char _char;
    private double _double;
    private float _float;
    private int _int;
    private long _long;
    private short _short;
    private byte[] _byteArr;
    private boolean[] _booleanArr;
    private char[] _charArr;
    private double[] _doubleArr;
    private float[] _floatArr;
    private int[] _intArr;
    private long[] _longArr;
    private short[] _shortArr;

    // Primitive Objects
    private Byte _byteObj;
    private Boolean _booleanObj;
    private Character _charObj;
    private Double _doubleObj;
    private Float _floatObj;
    private Integer _intObj;
    private Long _longObj;
    private Short _shortObj;
    private Byte[] _byteObjArr;
    private Boolean[] _booleanObjArr;
    private Character[] _charObjArr;
    private Double[] _doubleObjArr;
    private Float[] _floatObjArr;
    private Integer[] _intObjArr;
    private Long[] _longObjArr;
    private Short[] _shortObjArr;

    // Object
    private Object _private_object;
    public Object _public_object;
    protected Object _protected_object;
    Object _packagescope_object;
    private Object[] _objArr;

    // String
    private String nameString;
    private String[] nameStrings;

    // Collections
    private Collection collection;
    private List list;
    private Map map;
    private SortedMap sortedMap;
    private Set set;
    private SortedSet sortedSet;
    private Vector vector;
    private ArrayList arrayList;
    private LinkedList linkedList;
    private HashMap hashMap;
    private Hashtable hashtable;
    private TreeMap treeMap;
    private LinkedHashMap linkedHashMap;
    private HashSet hashSet;
    private TreeSet treeSet;
    private LinkedHashSet linkedHashSet;

    // Other frequent used objects
    private String _string;
    private java.util.Date _utilDate;
    private java.sql.Date _sqlDate;
    private java.sql.Time _sqlTime;
    private java.sql.Timestamp _sqlTimestamp;
    private BigDecimal bigDecimal;
    private BigInteger bigInteger;


    /**
     * Hello Claus this is DummyCompleteTestBean speaking!
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DummyCompleteTestBean");
        sb.append("{doNotStreamMe=").append(doNotStreamMe);
        sb.append(", _byte=").append(_byte);
        sb.append(", _boolean=").append(_boolean);
        sb.append(", _char=").append(_char);
        sb.append(", _double=").append(_double);
        sb.append(", _float=").append(_float);
        sb.append(", _int=").append(_int);
        sb.append(", _long=").append(_long);
        sb.append(", _short=").append(_short);
        sb.append(", _byteArr=").append(_byteArr == null ? "null" : "");
        for (int i = 0; _byteArr != null && i < _byteArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_byteArr[i]);
        sb.append(", _booleanArr=").append(_booleanArr == null ? "null" : "");
        for (int i = 0; _booleanArr != null && i < _booleanArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_booleanArr[i]);
        sb.append(", _charArr=").append(_charArr == null ? "null" : "");
        for (int i = 0; _charArr != null && i < _charArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_charArr[i]);
        sb.append(", _doubleArr=").append(_doubleArr == null ? "null" : "");
        for (int i = 0; _doubleArr != null && i < _doubleArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_doubleArr[i]);
        sb.append(", _floatArr=").append(_floatArr == null ? "null" : "");
        for (int i = 0; _floatArr != null && i < _floatArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_floatArr[i]);
        sb.append(", _intArr=").append(_intArr == null ? "null" : "");
        for (int i = 0; _intArr != null && i < _intArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_intArr[i]);
        sb.append(", _longArr=").append(_longArr == null ? "null" : "");
        for (int i = 0; _longArr != null && i < _longArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_longArr[i]);
        sb.append(", _shortArr=").append(_shortArr == null ? "null" : "");
        for (int i = 0; _shortArr != null && i < _shortArr.length; ++i)
            sb.append(i == 0 ? "" : ", ").append(_shortArr[i]);
        sb.append(", _byteObj=").append(_byteObj);
        sb.append(", _booleanObj=").append(_booleanObj);
        sb.append(", _charObj=").append(_charObj);
        sb.append(", _doubleObj=").append(_doubleObj);
        sb.append(", _floatObj=").append(_floatObj);
        sb.append(", _intObj=").append(_intObj);
        sb.append(", _longObj=").append(_longObj);
        sb.append(", _shortObj=").append(_shortObj);
        sb.append(", _byteObjArr=").append(_byteObjArr == null ? "null" : Arrays.asList(_byteObjArr).toString());
        sb.append(", _booleanObjArr=").append(_booleanObjArr == null ? "null" : Arrays.asList(_booleanObjArr).toString());
        sb.append(", _charObjArr=").append(_charObjArr == null ? "null" : Arrays.asList(_charObjArr).toString());
        sb.append(", _doubleObjArr=").append(_doubleObjArr == null ? "null" : Arrays.asList(_doubleObjArr).toString());
        sb.append(", _floatObjArr=").append(_floatObjArr == null ? "null" : Arrays.asList(_floatObjArr).toString());
        sb.append(", _intObjArr=").append(_intObjArr == null ? "null" : Arrays.asList(_intObjArr).toString());
        sb.append(", _longObjArr=").append(_longObjArr == null ? "null" : Arrays.asList(_longObjArr).toString());
        sb.append(", _shortObjArr=").append(_shortObjArr == null ? "null" : Arrays.asList(_shortObjArr).toString());
        sb.append(", _private_object=").append(_private_object);
        sb.append(", _public_object=").append(_public_object);
        sb.append(", _protected_object=").append(_protected_object);
        sb.append(", _packagescope_object=").append(_packagescope_object);
        sb.append(", _objArr=").append(_objArr == null ? "null" : Arrays.asList(_objArr).toString());
        sb.append(", nameString='").append(nameString).append('\'');
        sb.append(", nameStrings=").append(nameStrings == null ? "null" : Arrays.asList(nameStrings).toString());
        sb.append(", collection=").append(collection);
        sb.append(", list=").append(list);
        sb.append(", map=").append(map);
        sb.append(", sortedMap=").append(sortedMap);
        sb.append(", set=").append(set);
        sb.append(", sortedSet=").append(sortedSet);
        sb.append(", vector=").append(vector);
        sb.append(", arrayList=").append(arrayList);
        sb.append(", linkedList=").append(linkedList);
        sb.append(", hashMap=").append(hashMap);
        sb.append(", hashtable=").append(hashtable);
        sb.append(", treeMap=").append(treeMap);
        sb.append(", linkedHashMap=").append(linkedHashMap);
        sb.append(", hashSet=").append(hashSet);
        sb.append(", treeSet=").append(treeSet);
        sb.append(", linkedHashSet=").append(linkedHashSet);
        sb.append(", _string='").append(_string).append('\'');
        sb.append(", _utilDate=").append(_utilDate);
        sb.append(", _sqlDate=").append(_sqlDate);
        sb.append(", _sqlTime=").append(_sqlTime);
        sb.append(", _sqlTimestamp=").append(_sqlTimestamp);
        sb.append(", bigDecimal=").append(bigDecimal);
        sb.append(", bigInteger=").append(bigInteger);
        sb.append('}');
        return sb.toString();
    }

}

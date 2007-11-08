package com.intellij.util.io;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

/**
 * @author max
 */
public class WriteableMapAdapter<K,V> implements WriteableMap<V> {
  private Map<K,V> myMap;
  private ByteBufferMap.KeyProvider myKeyProvider;
  private K[] myKeys;

  public WriteableMapAdapter(Map<K,V> map, ByteBufferMap.KeyProvider provider) {
    myMap = map;
    myKeyProvider = provider;
    myKeys = (K[]) myMap.keySet().toArray();
  }

  public int[] getHashCodesArray() {
    int[] keyHashCodes = new int[ myKeys.length ];
    for( int i = 0; i < myKeys.length; i++ )
      keyHashCodes[i] = myKeyProvider.hashCode(myKeys[i]);
    return keyHashCodes;
  }

  public V getValue( int n ) {
    return myMap.get( myKeys[n] );
  }

  public int getKeyLength( int n ) {
    return myKeyProvider.length( myKeys[n] );
  }

  public void writeKey( DataOutput out, int n ) throws IOException {
    myKeyProvider.write( out, myKeys[n] );
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import java.util.Arrays;

/**
 * IdentityHashMap that have the (immutable) keys stored in values (2 times more compact).
 */
final class AddonlyKeylessHash<K, V> {
  private int size;
  private Object[] entries;
  private final KeyValueMapper<K, V> keyValueMapper;

  AddonlyKeylessHash(KeyValueMapper<K, V> _keyValueMapper) {
    this(4, _keyValueMapper);
  }

  AddonlyKeylessHash(int expectedSize, KeyValueMapper<K, V> _keyValueMapper) {
    int i = PrimeFinder.nextPrime(5 * expectedSize / 4 + 1);
    entries = new Object[i];
    keyValueMapper = _keyValueMapper;
  }

  public int size() {
    return size;
  }

  public void add(V item) {
    if (size >= (4 * entries.length) / 5) rehash();
    V v = doPut(entries, item);
    if (v == null) size++;
  }

  private V doPut(Object[] a, V o) {
    K key = keyValueMapper.key(o);
    int index = hashIndex(a, key);
    V obj = (V)a[index];
    a[index] = o;
    return obj;
  }

  private int hashIndex(Object[] a, K key) {
    int hash = keyValueMapper.hash(key) & 0x7fffffff;
    int index = hash % a.length;
    V candidate = (V)a[index];
    if (candidate == null || keyValueMapper.valueHasKey(candidate, key)) {
      return index;
    }

    final int probe = 1 + (hash % (a.length - 2));

    do {
      index -= probe;
      if (index < 0) index += a.length;

      candidate = (V)a[index];
    }
    while (candidate != null && !keyValueMapper.valueHasKey(candidate, key));

    return index;
  }

  private void rehash() {
    Object[] b = new Object[PrimeFinder.nextPrime(entries.length * 2)];
    for (int i = entries.length; --i >= 0; ) {
      V ns = (V)entries[i];
      if (ns != null) doPut(b, ns);
    }
    entries = b;
  }

  public V get(K key) {
    return (V)entries[hashIndex(entries, key)];
  }

  public abstract static class KeyValueMapper<K, V> {
    public abstract int hash(K k);

    public abstract K key(V v);

    public boolean valueHasKey(V value, K key) {
      return key == key(value); // identity
    }

    protected boolean isIdentity() { return true; }
  }
}


/**
 * Used to keep hash table capacities prime numbers.
 * Not of interest for users; only for implementors of hashtables.
 *
 * <p>Choosing prime numbers as hash table capacities is a good idea
 * to keep them working fast, particularly under hash table
 * expansions.
 *
 * <p>However, JDK 1.2, JGL 3.1 and many other toolkits do nothing to
 * keep capacities prime.  This class provides efficient means to
 * choose prime capacities.
 *
 * <p>Choosing a prime is <tt>O(log 300)</tt> (binary search in a list
 * of 300 ints).  Memory requirements: 1 KB static memory.
 *
 * @author wolfgang.hoschek@cern.ch
 * @version 1.0, 09/24/99
 */
final class PrimeFinder {
	/**
	 * The largest prime this class can generate; currently equal to
	 * <tt>Integer.MAX_VALUE</tt>.
	 */
	public static final int largestPrime = Integer.MAX_VALUE; //yes, it is prime.

	/**
	 * The prime number list.
	 * Primes are chosen such that for any desired capacity >= 1000
	 * the list includes a prime number <= desired capacity * 1.11.
     *
	 * Therefore, primes can be retrieved which are quite close to any
	 * desired capacity, which in turn avoids wasting memory.
     *
	 * For example, the list includes
	 * 1039,1117,1201,1277,1361,1439,1523,1597,1759,1907,2081.
     *
	 * So if you need a prime >= 1040, you will find a prime <=
	 * 1040*1.11=1154.
	 *
	 * Primes are chosen such that they are optimized for a hashtable
	 * growthfactor of 2.0;
     *
	 * If your hashtable has such a growthfactor then, after initially
	 * "rounding to a prime" upon hashtable construction, it will
	 * later expand to prime capacities such that there exist no
	 * better primes.
	 *
	 * In total these are about 32*10=320 numbers -> 1 KB of static
	 * memory needed.
	 */

	private static final int[] primeCapacities = {
		3, 5, 7, 11, 17, 23, 31, 37, 43, 47, 67, 79, 89, 97, 137, 163, 179, 197, 277, 311, 331, 359, 379, 397, 433, 557,
		599, 631, 673, 719, 761, 797, 877, 953, 1039, 1117, 1201, 1277, 1361, 1439, 1523, 1597, 1759, 1907, 2081, 2237,
		2411, 2557, 2729, 2879, 3049, 3203, 3527, 3821, 4177, 4481, 4831, 5119, 5471, 5779, 6101, 6421, 7057, 7643, 8363,
		8963, 9677, 10243, 10949, 11579, 12203, 12853, 14143, 15287, 16729, 17929, 19373, 20507, 21911, 23159, 24407,
		25717, 28289, 30577, 33461, 35863, 38747, 41017, 43853, 46327, 48817, 51437, 56591, 61169, 66923, 71741, 77509,
		82037, 87719, 92657, 97649, 102877, 113189, 122347, 133853, 143483, 155027, 164089, 175447, 185323, 195311, 205759,
		226379, 244703, 267713, 286973, 310081, 328213, 350899, 370661, 390647, 411527, 452759, 489407, 535481, 573953,
		620171, 656429, 701819, 741337, 781301, 823117, 905551, 978821, 1070981, 1147921, 1240361, 1312867, 1403641,
		1482707, 1562611, 1646237, 1811107, 1957651, 2141977, 2295859, 2480729, 2625761, 2807303, 2965421, 3125257,
		3292489, 3622219, 3915341, 4283963, 4591721, 4961459, 5251529, 5614657, 5930887, 6250537, 6584983, 7244441,
		7830701, 8567929, 9183457, 9922933, 10503061, 11229331, 11861791, 12501169, 13169977, 14488931, 15661423,
		17135863, 18366923, 19845871, 21006137, 22458671, 23723597, 25002389, 26339969, 28977863, 31322867, 34271747,
		36733847, 39691759, 42012281, 44917381, 47447201, 50004791, 52679969, 57955739, 62645741, 68543509, 73467739,
		79383533, 84024581, 89834777, 94894427, 100009607, 105359939, 115911563, 125291483, 137087021, 146935499,
		158767069, 168049163, 179669557, 189788857, 200019221, 210719881, 231823147, 250582987, 274174111, 293871013,
		317534141, 336098327, 359339171, 379577741, 400038451, 421439783, 463646329, 501165979, 548348231, 587742049,
		635068283, 672196673, 718678369, 759155483, 800076929, 842879579, 927292699, 1002331963, 1096696463, 1175484103,
		1270136683, 1344393353, 1437356741, 1518310967, 1600153859, 1685759167, 1854585413, 2004663929, 2147483647
	};

    /**
     * Returns a prime number which is <code>&gt;= desiredCapacity</code>
     * and very close to <code>desiredCapacity</code> (within 11% if
     * <code>desiredCapacity &gt;= 1000</code>).
     *
     * @param desiredCapacity the capacity desired by the user.
     * @return the capacity which should be used for a hashtable.
     */
    public static int nextPrime(int desiredCapacity) {
        int i = Arrays.binarySearch(primeCapacities, desiredCapacity);
        if (i<0) {
            // desired capacity not found, choose next prime greater
            // than desired capacity
            i = -i -1; // remember the semantics of binarySearch...
        }
        return primeCapacities[i];
    }
}
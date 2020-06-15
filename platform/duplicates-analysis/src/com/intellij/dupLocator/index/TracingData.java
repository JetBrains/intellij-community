package com.intellij.dupLocator.index;

import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntListIterator;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TracingData {
  private static final String tracingDataLocation = "E:\\ultimate\\system\\occurrences";
  private final PersistentHashMap<Integer, Integer> keys;
  private final ScheduledThreadPoolExecutor pool = ConcurrencyUtil.newSingleScheduledThreadExecutor("My flushing thread");

  private final AtomicInteger maxHash = new AtomicInteger();
  private final AtomicInteger maxValue = new AtomicInteger();
  private ScheduledFuture<?> flushingFuture;

  TracingData() {
    PersistentHashMap<Integer, Integer> lkeys = null;
    try {
      lkeys = createOrOpenMap();
      flushingFuture = pool.scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          if (keys.isDirty() && !keys.isClosed()) {
            keys.force();
          }
        }
      }, 5, 5, TimeUnit.SECONDS);
      ShutDownTracker.getInstance().registerShutdownTask(() -> flushingFuture.cancel(false));
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    keys = lkeys;
  }

  private static PersistentHashMap<Integer, Integer> createOrOpenMap() throws IOException {
    return new PersistentHashMap<>(Paths.get(tracingDataLocation), EnumeratorIntegerDescriptor.INSTANCE,
                                   EnumeratorIntegerDescriptor.INSTANCE);
  }

  public void record(int hash, int cost, PsiFragment frag) {
    //System.out.println(frag.getElements()[0].getText());

    if (keys != null) {
      try {
        Integer integer = keys.get(hash);
        int value = integer != null ? integer + 1 : 1;
        keys.put(hash, value);
        int currentMaxValue = maxValue.get();
        while (value > currentMaxValue) {
          if (maxValue.compareAndSet(currentMaxValue, value)) {
            maxHash.set(hash);
            System.out.println(maxValue + "," + maxHash + ","+frag.getElements()[0].getText());
            break;
          }
          currentMaxValue = maxValue.get();
        }
      }
      catch (IOException ex) {
        ex.printStackTrace();
      }
    }
  }

  public static void main(String[] args) throws IOException {
    PersistentHashMap<Integer, Integer> lKeys = createOrOpenMap();
    IntArrayList mapping = new IntArrayList();
    lKeys.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(mapping));
    System.out.println(mapping.size());
    Int2IntOpenHashMap map = new Int2IntOpenHashMap(mapping.size());
    for (IntListIterator iterator = mapping.iterator(); iterator.hasNext(); ) {
      int i = iterator.nextInt();
      map.put(i, lKeys.get(i).intValue());
    }

    mapping.sort((o1, o2) -> map.get(o2) - map.get(o1));

    for(int i = 0; i < 500; ++i) {
      //System.out.println(mapping.get(i) + ",");
      System.out.println(mapping.getInt(i) + ":" + map.get(mapping.getInt(i)));
    }
    lKeys.close();
  }
}

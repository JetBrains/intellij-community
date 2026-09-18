package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.platform.util.io.storages.CommonKeyDescriptors;
import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.InlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Contains 'hacks' used in the transition period to adapt already existing {@link KeyDescriptor}/{@link DataExternalizer}
 * to the new {@link ByteBuffer}-based API.
 * <p>
 * In a long-term I'd expect most implementations to be 'bilingual' -- i.e. implement both old and new interfaces, with
 * few not-performance-critical implementations still adapted via {@link KeyDescriptorEx#adapt(KeyDescriptor)}/{@link DataExternalizerEx#adapt(DataExternalizer)}.
 * But during the transition period it is less intrusive to just recognize specific externalizers, and substitute
 * them with explicitly implemented 'analogs'. I don't want these substitution logic to spread through the codebase
 * -- much better to have it contained in a single place, explicitly marked as 'temporary/for transition period'
 * -- it is much clearer, and easier to rectify in future.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
public final class LegacyAdapter {

  public static <K> @NotNull KeyDescriptorEx<K> adapt(@NotNull KeyDescriptor<K> oldSchoolDescriptor) {
    //FIXME RC: in a long term it is better to have EnumeratorIntegerDescriptor implement both KeyDescriptor & KeyDescriptorEx
    if (oldSchoolDescriptor instanceof EnumeratorIntegerDescriptor) {
      //noinspection unchecked
      return (KeyDescriptorEx<K>)CommonKeyDescriptors.integer();
    }

    //This captures IdIndex.myKeyDescriptor, and a few others:
    if (oldSchoolDescriptor instanceof InlineKeyDescriptor<K> inlineKeyDescriptor) {
      //FIXME RC: in a long term it is better to make them all implement both KeyDescriptor & KeyDescriptorEx
      return new InlineKeyDescriptorAdapter<>(inlineKeyDescriptor);
    }

    return KeyDescriptorEx.adapt(oldSchoolDescriptor);
  }

  public static <V> @NotNull DataExternalizerEx<V> adapt(@NotNull DataExternalizer<V> externalizer) {
    if (externalizer instanceof @NotNull KeyDescriptor<V>) {
      return adapt((KeyDescriptor<V>)externalizer);
    }
    return DataExternalizerEx.adapt(externalizer);
  }

  //TODO RC: current implementation ignores InlineKeyDescriptor.isCompactFormat() -- because it is protected, but also
  //         because with !compact format serializer becomes not constant-size-serializer
  private static class InlineKeyDescriptorAdapter<K> implements KeyDescriptorEx<K> {
    private final InlineKeyDescriptor<K> adaptedDescriptor;

    private InlineKeyDescriptorAdapter(@NotNull InlineKeyDescriptor<K> adaptedDescriptor) { this.adaptedDescriptor = adaptedDescriptor; }

    @Override
    public int getHashCode(K value) {
      return adaptedDescriptor.getHashCode(value);
    }

    @Override
    public boolean isEqual(K key1, K key2) {
      return adaptedDescriptor.isEqual(key1, key2);
    }

    @Override
    public int recordSizeIfConstant() {
      return Integer.BYTES;
    }

    @Override
    public K read(@NotNull ByteBuffer input)  {
      int code = input.getInt();
      return adaptedDescriptor.fromInt(code);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull K value) {
      int code = adaptedDescriptor.toInt(value);
      return new IntRecordWriter(code);
    }

    private record IntRecordWriter(int code) implements KnownSizeRecordWriter {
      @Override
      public ByteBuffer write(@NotNull ByteBuffer data) {
        return data.putInt(code);
      }

      @Override
      public int recordSize() {
        return Integer.BYTES;
      }
    }
  }
}

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.*;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

@SuppressWarnings({"PointlessArithmeticExpression", "HardCodedStringLiteral"})
public class FSRecords {
  private static final Logger LOG = Logger.getInstance("#com.intellij.vfs.persistent.FSRecords");

  private final static int VERSION = 1;

  private static final int PARENT_OFFSET = 0;
  private static final int PARENT_SIZE = 4;
  private static final int NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE;
  private static final int NAME_SIZE = 4;
  private static final int FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE;
  private static final int FLAGS_SIZE = 4;
  private static final int ATTREF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE;
  private static final int ATTREF_SIZE = 4;
  private static final int CRC_OFFSET = ATTREF_OFFSET + ATTREF_SIZE;
  private static final int CRC_SIZE = 8;
  private static final int TIMESTAMP_OFFSET = CRC_OFFSET + CRC_SIZE;
  private static final int TIMESTAMP_SIZE = 8;
  private static final int MODCOUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  private static final int MODCOUNT_SIZE = 4;
  private static final int LENGTH_OFFSET = MODCOUNT_OFFSET + MODCOUNT_SIZE;
  private static final int LENGTH_SIZE = 4;

  private final static int RECORD_SIZE = LENGTH_OFFSET + LENGTH_SIZE;

  private MappedFile myRecords;
  private PersistentStringEnumerator myNames;
  private final TObjectIntHashMap<String> myAttributeIds = new TObjectIntHashMap<String>();
  private PagedMemoryMappedFile myAttributes;
  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  private static final int HEADER_VERSION_OFFSET = 0;
  private static final int HEADER_FREE_RECORD_OFFSET = 4;
  private static final int HEADER_GLOBAL_MODCOUNT_OFFSET = 8;
  private static final int HEADER_CONNECTION_STATUS_OFFSET = 12;
  private static final int HEADER_SIZE = HEADER_CONNECTION_STATUS_OFFSET + 4;

  private static final int CONNECTED_MAGIC = 0x12ad34e4;
  private static final int SAFELY_CLOSED_MAGIC = 0x1f2f3f4f;

  private static final String CHILDREN_ATT = "FsRecords.DIRECTORY_CHILDREN";
  private final File myRecordsFile;
  private final File myNamesFile;
  private final File myAttributesFile;

  static {
    //noinspection ConstantConditions
    assert HEADER_SIZE <= RECORD_SIZE;
  }

  public FSRecords(String urlfor) {
    final String baseFileName = PathManager.getSystemPath() + "/caches/" + Integer.toHexString(urlfor.hashCode());
    myRecordsFile = new File(baseFileName + ".records");
    myNamesFile = new File(baseFileName + ".names");
    myAttributesFile = new File(baseFileName + ".attributes");
  }

  public void connect() throws IOException {
    myRecords = new MappedFile(myRecordsFile, 20 * 1024);
    myNames = new PersistentStringEnumerator(myNamesFile);
    myAttributes = new PagedMemoryMappedFile(myAttributesFile);

    if (myRecords.length() == 0) {
      cleanRecord(0); // Clean header
      cleanRecord(1); // Create root record
      setCurrentVersion();
    }

    if (getVersion() != VERSION) {
      throw new IOException("FS repository version mismatch");
    }

    if (myRecords.getInt(HEADER_CONNECTION_STATUS_OFFSET) != SAFELY_CLOSED_MAGIC) {
      throw new IOException("FS repostiory wasn't safely shut down");
    }

    myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, CONNECTED_MAGIC);
  }

  private int getVersion() throws IOException {
    return myRecords.getInt(HEADER_VERSION_OFFSET);
  }

  private void setCurrentVersion() throws IOException {
    myRecords.putInt(HEADER_VERSION_OFFSET, VERSION);
    myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
  }

  public int createRecord() {
    try {
      final int next = myRecords.getInt(HEADER_FREE_RECORD_OFFSET);

      if (next == 0) {
        final int filelength = (int)myRecords.length();
        LOG.assertTrue(filelength % RECORD_SIZE == 0);
        int result = filelength / RECORD_SIZE;
        cleanRecord(result);
        return result;
      }
      else {
        myRecords.putInt(HEADER_FREE_RECORD_OFFSET, getNextFree(next));
        setNextFree(next, 0);
        return next;
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteRecordRecursively(int id) {
    try {
      incModCount(id);
      doDeleteRecursively(id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doDeleteRecursively(final int id) {
    for (int subrecord : list(id)) {
      doDeleteRecursively(subrecord);
    }

    deleteRecord(id);
  }

  public void deleteRecord(final int id) {
    try {
      int att_page = myRecords.getInt(id * RECORD_SIZE + ATTREF_OFFSET);

      while (att_page != 0) {
        final RandomAccessPagedDataInput page = myAttributes.getReader(att_page);
        page.readInt(); // Skip att_id
        final int next = page.readInt();
        page.close();
        myAttributes.delete(att_page);
        att_page = next;
      }

      cleanRecord(id);
      addToFreeRecordsList(id);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void addToFreeRecordsList(int id) throws IOException {
    final int next = myRecords.getInt(HEADER_FREE_RECORD_OFFSET);
    setNextFree(id, next);
    myRecords.putInt(HEADER_FREE_RECORD_OFFSET, id);
  }

  public int[] list(int id) {
    try {
      final RandomAccessPagedDataInput input = readAttribute(id, CHILDREN_ATT);
      if (input == null) return new int[0];

      final int count = input.readInt();
      final int[] result = new int[count];
      for (int i = 0; i < count; i++) {
        result[i] = input.readInt();
      }
      input.close();
      return result;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void updateList(int id, int[] children) {
    try {
      final RecordDataOutput record = writeAttribute(id, CHILDREN_ATT);
      record.writeInt(children.length);
      for (int child : children) {
        record.writeInt(child);
      }
      record.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void incModCount(int id) throws IOException {
    final int count = getModCount() + 1;
    myRecords.putInt(HEADER_GLOBAL_MODCOUNT_OFFSET, count);

    int parent = id;
    while (parent != 0) {
      setModCount(parent, count);
      parent = getParent(parent);
    }
  }

  public int getModCount()  {
    try {
      return myRecords.getInt(HEADER_GLOBAL_MODCOUNT_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void cleanRecord(final int id) throws IOException {
    myRecords.put(id * RECORD_SIZE, ZEROES, 0, RECORD_SIZE);
  }

  public int getParent(int id) {
    try {
      return myRecords.getInt(id * RECORD_SIZE + PARENT_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setParent(int id, int parent) {
    try {
      incModCount(id);
      myRecords.putInt(id * RECORD_SIZE + PARENT_OFFSET, parent);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int getNextFree(int id) {
    return getParent(id);
  }

  private void setNextFree(int id, int next) {
    try {
      myRecords.putInt(id * RECORD_SIZE + PARENT_OFFSET, next);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String getName(int id) {
    try {
      final int nameId = myRecords.getInt(id * RECORD_SIZE + NAME_OFFSET);
      return nameId != 0 ? myNames.valueOf(nameId) : "";
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setName(int id, String name) {
    try {
      incModCount(id);
      myRecords.putInt(id * RECORD_SIZE + NAME_OFFSET, myNames.enumerate(name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getFlags(int id) {
    try {
      return myRecords.getInt(id * RECORD_SIZE + FLAGS_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setFlags(int id, int flags) {
    try {
      incModCount(id);
      myRecords.putInt(id * RECORD_SIZE + FLAGS_OFFSET, flags);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getLength(int id) {
    try {
      return myRecords.getInt(id * RECORD_SIZE + LENGTH_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setLength(int id, int len) {
    try {
      incModCount(id);
      myRecords.putInt(id * RECORD_SIZE + LENGTH_OFFSET, len);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getCRC(int id) {
    try {
      return myRecords.getLong(id * RECORD_SIZE + CRC_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setCRC(int id, long crc) {
    try {
      incModCount(id);
      myRecords.putLong(id * RECORD_SIZE + CRC_OFFSET, crc);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getTimestamp(int id) {
    try {
      return myRecords.getLong(id * RECORD_SIZE + TIMESTAMP_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setTimestamp(int id, long value) {
    try {
      incModCount(id);
      myRecords.putLong(id * RECORD_SIZE + TIMESTAMP_OFFSET, value);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getModCount(int id) {
    try {
      return myRecords.getInt(id * RECORD_SIZE + MODCOUNT_OFFSET);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void setModCount(int id, int value) throws IOException {
    myRecords.putInt(id * RECORD_SIZE + MODCOUNT_OFFSET, value);
  }

  private int getAttributeId(String attId) throws IOException {
    if (myAttributeIds.containsKey(attId)) {
      return myAttributeIds.get(attId);
    }

    int id = myNames.enumerate(attId);
    myAttributeIds.put(attId, id);
    return id;
  }

  @Nullable
  public RandomAccessPagedDataInput readAttribute(int id, String attId) {
    try {
      int encodedAttId = getAttributeId(attId);
      int att_page = myRecords.getInt(id * RECORD_SIZE + ATTREF_OFFSET);
      while (att_page != 0) {
        final RandomAccessPagedDataInput page = myAttributes.getReader(att_page);
        final int attIdOnPage = page.readInt();
        final int next = page.readInt();
        if (attIdOnPage == encodedAttId) {
          return page;
        }
        att_page = next;
        page.close();
      }

      return null;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public RecordDataOutput writeAttribute(int id, String attId) {
    try {
      incModCount(id);

      int encodedAttId = getAttributeId(attId);
      final int headPage = myRecords.getInt(id * RECORD_SIZE + ATTREF_OFFSET);
      int att_page = headPage;

      while (att_page != 0) {
        int curPage = att_page;
        final RandomAccessPagedDataInput page = myAttributes.getReader(att_page);
        final int attIdOnPage = page.readInt();
        final int next = page.readInt();
        if (attIdOnPage == encodedAttId) {
          page.close();
          final RecordDataOutput result = myAttributes.getWriter(curPage);
          result.writeInt(encodedAttId);
          result.writeInt(next);

          return result;
        }

        att_page = page.readInt();
        page.close();
      }

      final RecordDataOutput result = myAttributes.createRecord();
      myRecords.putInt(id * RECORD_SIZE + ATTREF_OFFSET, result.getRecordId());

      result.writeInt(encodedAttId);
      result.writeInt(headPage);

      return result;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void disposeAndDeleteFiles() {
    dispose();

    FileUtil.delete(myNamesFile);
    FileUtil.delete(myAttributesFile);
    FileUtil.delete(myRecordsFile);
  }

  public void dispose() {
    try {
      if (myAttributes != null) {
        myAttributes.dispose();
      }
      if (myNames != null) {
        myNames.close();
      }
      if (myRecords != null) {
        myRecords.putInt(HEADER_CONNECTION_STATUS_OFFSET, SAFELY_CLOSED_MAGIC);
        myRecords.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
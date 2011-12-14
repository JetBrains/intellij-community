package com.intellij.openapi.vfs;

import com.intellij.idea.Bombed;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;

public class LocalFileSystemTest extends IdeaTestCase{
  private static final String KEY = "filesystem.useNative";

  public static void setContentOnDisk(File file, byte[] bom, String content, Charset charset) throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    if (bom != null) {
      stream.write(bom);
    }
    OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
    writer.write(content);
    writer.close();
  }

  public static VirtualFile createTempFile(@NonNls String ext, @Nullable byte[] bom, @NonNls String content, Charset charset) throws IOException {
    File temp = FileUtil.createTempFile("copy", "." + ext);
    setContentOnDisk(temp, bom, content, charset);

    myFilesToDelete.add(temp);
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(temp);
  }

  public void testChildrenAccessedButNotCached() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            File dir = createTempDirectory();
            final ManagingFS managingFS = ManagingFS.getInstance();

            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(vFile);
            assertFalse(managingFS.areChildrenLoaded(vFile));
            assertFalse(managingFS.wereChildrenAccessed(vFile));

            final File child = new File(dir, "child");
            final boolean created = child.createNewFile();
            assertTrue(created);

            final File subdir = new File(dir, "subdir");
            final boolean subdirCreated = subdir.mkdir();
            assertTrue(subdirCreated);

            final File subChild = new File(subdir, "subdir");
            final boolean subChildCreated = subChild.createNewFile();
            assertTrue(subChildCreated);

            final VirtualFile childVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(child.getPath().replace(File.separatorChar, '/'));
            assertNotNull(childVFile);
            assertFalse(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));

            final VirtualFile subdirVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(subdir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(subdirVFile);
            assertFalse(managingFS.areChildrenLoaded(subdirVFile));
            assertFalse(managingFS.wereChildrenAccessed(subdirVFile));

            assertFalse(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));
            
            
            vFile.getChildren();
            assertTrue(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));
            assertFalse(managingFS.areChildrenLoaded(subdirVFile));
            assertFalse(managingFS.wereChildrenAccessed(subdirVFile));
            
            final VirtualFile subChildVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(subChild.getPath().replace(File.separatorChar, '/'));
            assertNotNull(subChildVFile);
            assertTrue(managingFS.areChildrenLoaded(vFile));
            assertTrue(managingFS.wereChildrenAccessed(vFile));
            assertFalse(managingFS.areChildrenLoaded(subdirVFile));
            assertTrue(managingFS.wereChildrenAccessed(subdirVFile));
          }
          catch(IOException e){
            LOG.error(e);
          }
        }
      }
    );
  }
  
  public void testRefreshAndFindFile() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            File dir = createTempDirectory();

            VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(vFile);
            vFile.getChildren();

            for(int i = 0; i < 100; i++){
              File subdir = new File(dir, "a" + i);
              assertTrue(subdir.mkdir());
            }

            File subdir = new File(dir, "aaa");
            assertTrue(subdir.mkdir());

            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(subdir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(file);
          }
          catch(IOException e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testCopyFile() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try {
            File fromDir = createTempDirectory();
            File toDir = createTempDirectory();

            VirtualFile fromVDir = LocalFileSystem.getInstance().findFileByPath(fromDir.getPath().replace(File.separatorChar, '/'));
            VirtualFile toVDir = LocalFileSystem.getInstance().findFileByPath(toDir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(fromVDir);
            assertNotNull(toVDir);
            final VirtualFile fileToCopy = fromVDir.createChildData(null, "temp_file");
            final byte[] byteContent = {0, 1, 2, 3};
            fileToCopy.setBinaryContent(byteContent);
            final String newName = "new_temp_file";
            final VirtualFile copy = fileToCopy.copy(null, toVDir, newName);
            assertEquals(newName, copy.getName());
            assertTrue(Arrays.equals(byteContent, copy.contentsToByteArray()));
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testCopyDir() throws Exception{
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            File fromDir = createTempDirectory();
            File toDir = createTempDirectory();

            VirtualFile fromVDir = LocalFileSystem.getInstance().findFileByPath(fromDir.getPath().replace(File.separatorChar, '/'));
            VirtualFile toVDir = LocalFileSystem.getInstance().findFileByPath(toDir.getPath().replace(File.separatorChar, '/'));
            assertNotNull(fromVDir);
            assertNotNull(toVDir);
            final VirtualFile dirToCopy = fromVDir.createChildDirectory(null, "dir");
            final VirtualFile file = dirToCopy.createChildData(null, "temp_file");
            file.setBinaryContent(new byte[]{0, 1, 2, 3});
            final String newName = "dir";
            final VirtualFile dirCopy = dirToCopy.copy(null, toVDir, newName);
            assertEquals(newName, dirCopy.getName());
            IdeaTestUtil.assertDirectoriesEqual(toVDir, fromVDir, null);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testGermanLetters() throws Exception{
    final File dirFile = createTempDirectory();

    final String name = "te\u00dft123123123.txt";
    final File childFile = new File(dirFile, name);
    childFile.createNewFile();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          try{
            final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dirFile);
            assertNotNull(dir);

            final VirtualFile child = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(childFile);
            assertNotNull(child);

          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );


    assertTrue(childFile.delete());
  }

  public void _testSymLinks() throws Exception {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          boolean b = Registry.get(KEY).asBoolean();
          try{
            Registry.get(KEY).setValue(false);
            final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath("C:/Documents and Settings");
            Win32LocalFileSystem system = Win32LocalFileSystem.getWin32Instance();
            system.exists(dir);
            String[] strings = system.list(dir);
            System.out.println(Arrays.asList(strings));
          }
          catch(Exception e){
            fail(e.getMessage());
          }
          finally {
            Registry.get(KEY).setValue(b);
          }
        }
      }
    );
  }

  public void testFindRoot() {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath("wrong_path");
    assertNull(file);

    if (SystemInfo.isWindows) {
      assertNotNull(LocalFileSystem.getInstance().findFileByPath("\\\\unit-133"));
      assertNotNull(LocalFileSystem.getInstance().findFileByIoFile(new File("\\\\unit-133")));
    }

    if (SystemInfo.isWindows && new File("c:").exists()) {
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath("c:");
      assertNotNull(root);
    }
    if (SystemInfo.isUnix) {
      VirtualFile root = LocalFileSystem.getInstance().findFileByPath("/");
      assertNotNull(root);
    }

    VirtualFile root = LocalFileSystem.getInstance().findFileByPath("");
    assertNotNull(root);
  }

  public void testFileLength() throws Exception {

    File file = FileUtil.createTempFile("test", "txt");
    FileUtil.writeToFile(file, "hello");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    String s = VfsUtil.loadText(virtualFile);
    assertEquals("hello", s);
    assertEquals(5, virtualFile.getLength());

    FileUtil.writeToFile(file, "new content");
    ((PersistentFS)ManagingFS.getInstance()).cleanPersistedContents();
    s = VfsUtil.loadText(virtualFile);
    assertEquals("new content", s);
    assertEquals(11, virtualFile.getLength());
  }

  public void _testHardLinks() throws Exception {
    if (SystemInfo.isWindows) {
      File dir = FileUtil.createTempDirectory("hardlinks", "");
      File oldfile = new File(dir, "oldfile");
      assertTrue(oldfile.createNewFile());
      File newfile = new File(dir, "newfile");
      Process process = Runtime.getRuntime().exec(
        new String[]{"fsutil", "hardlink", "create", '"' + newfile.getPath() + '"', '"' + oldfile.getPath() + '"'});
      InputStream stream = process.getInputStream();
      System.out.println(new String(FileUtil.loadBytes(stream)));
      process.waitFor();
      VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(oldfile);
      assertNotNull(file);
      file.setBinaryContent("hello".getBytes(), 0, 0, new SafeWriteRequestor() {});
      VirtualFile check = LocalFileSystem.getInstance().findFileByIoFile(newfile);
      assertNotNull(check);
      assertEquals("hello", VfsUtil.loadText(check));
    }
  }
}

package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Arrays;

public class LocalFileSystemTest extends IdeaTestCase{
  private static final String KEY = "filesystem.useNative";

  public static void setContentOnDisk(File file, byte[] bom, String content, Charset charset) throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    stream.write(bom);
    OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
    writer.write(content);
    writer.close();
  }

  public static VirtualFile createTempFile(@NonNls String ext, byte[] bom, @NonNls String content, Charset charset) throws IOException {
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
}

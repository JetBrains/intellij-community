package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Vladimir Kondratyev
 */
final class FileTree{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.todo.FileTree");
  /**
   * The ArrayList contains PsiFiles and PsiDirectories.
   * Note that all these PsiDirectories has hildren. It means that these directory is only key
   * in this map.
   */
  private final HashMap<VirtualFile,ArrayList<VirtualFile>> myDirectory2Children;
  private final HashSet<VirtualFile> myFiles;
  private final HashMap<VirtualFile, ArrayList<VirtualFile> > myStrictDirectory2Children;

  FileTree(){
    myDirectory2Children=new HashMap<VirtualFile,ArrayList<VirtualFile>>();
    myFiles=new HashSet<VirtualFile>();
    myStrictDirectory2Children = new HashMap<VirtualFile, ArrayList<VirtualFile>>();
  }

  void add(VirtualFile file){
    if(myFiles.contains(file)){
      return;
    }

    myFiles.add(file);
    VirtualFile dir=file.getParent();
    LOG.assertTrue(dir!=null);

    ArrayList<VirtualFile> children=myStrictDirectory2Children.get(dir);
    if(children!=null){
      LOG.assertTrue(!children.contains(file));
      children.add(file);
    }else{
      children=new ArrayList<VirtualFile>(2);
      children.add(file);
      myStrictDirectory2Children.put(dir,children);
    }

    children=myDirectory2Children.get(dir);
    if(children!=null){
      LOG.assertTrue(!children.contains(file));
      children.add(file);
      return;
    }else{
      children=new ArrayList<VirtualFile>(2);
      children.add(file);
      myDirectory2Children.put(dir,children);
    }

    VirtualFile parent=dir.getParent();
    while(parent!=null){
      children=myDirectory2Children.get(parent);
      if(children!=null){
        if((!children.contains(dir))){
          children.add(dir);
        }
        return;
      }else{
        children=new ArrayList<VirtualFile>(2);
        children.add(dir);
        myDirectory2Children.put(parent,children);
      }
      dir=parent;
      parent=parent.getParent();
    }
  }

  boolean isDirectoryEmpty(VirtualFile dir){
    final ArrayList<VirtualFile> files = myStrictDirectory2Children.get(dir);
    return files == null || files.isEmpty();
  }

  ArrayList<VirtualFile> getFilesUnderDirectory(VirtualFile dir){
    ArrayList<VirtualFile> filesList=new ArrayList<VirtualFile>();
    final ArrayList<VirtualFile> files = myStrictDirectory2Children.get(dir);
    if (files != null){
      filesList.addAll(files);
    }
    return filesList;
  }

  void removeFile(VirtualFile file){
    if(!myFiles.contains(file)){
      return;
    }

    myFiles.remove(file);
    ArrayList<VirtualFile> dirsToBeRemoved=null;
    for (VirtualFile _directory : myDirectory2Children.keySet()) {
      ArrayList<VirtualFile> children = myDirectory2Children.get(_directory);
      LOG.assertTrue(children != null);
      if (children.contains(file)) {
        children.remove(file);
        if (children.size() == 0) {
          if (dirsToBeRemoved == null) {
            dirsToBeRemoved = new ArrayList<VirtualFile>(2);
          }
          dirsToBeRemoved.add(_directory); // we have to remove empty _directory
        }
      }
    }
    for (VirtualFile dir : myStrictDirectory2Children.keySet()) {
      final ArrayList<VirtualFile> children = myStrictDirectory2Children.get(dir);
      LOG.assertTrue(children!=null);
      if(children.contains(file)){
        children.remove(file);
      }
    }
    // We have remove also all removed (empty) directories
    if(dirsToBeRemoved!=null){
      LOG.assertTrue(dirsToBeRemoved.size()>0);
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  /**
   * The method removes specified <code>psiDirectory</code> from the tree. The directory should be empty,
   * otherwise the method thows java.lang.IllegalArgumentException
   */
  private void removeDir(VirtualFile psiDirectory){
    if(!myDirectory2Children.containsKey(psiDirectory)){
      throw new IllegalArgumentException("directory is not in the tree: "+psiDirectory);
    }
    ArrayList<VirtualFile> children=myDirectory2Children.remove(psiDirectory);
    if(children==null){
      throw new IllegalArgumentException("directory has no children list: "+psiDirectory);
    }
    if(children.size()>0){
      throw new IllegalArgumentException("directory isn't empty: "+psiDirectory);
    }
    //
    myStrictDirectory2Children.remove(psiDirectory);
    ArrayList<VirtualFile> dirsToBeRemoved=null;
    for (VirtualFile _directory : myDirectory2Children.keySet()) {
      children = myDirectory2Children.get(_directory);
      LOG.assertTrue(children != null);
      if (children.contains(psiDirectory)) {
        children.remove(psiDirectory);
        if (children.size() == 0) {
          if (dirsToBeRemoved == null) {
            dirsToBeRemoved = new ArrayList<VirtualFile>(2);
          }
          dirsToBeRemoved.add(_directory); // we have remove empty _directory
        }
      }
    }
    //
    if(dirsToBeRemoved!=null){
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  boolean contains(VirtualFile file){
    return myFiles.contains(file);
  }

  void clear(){
    myStrictDirectory2Children.clear();
    myDirectory2Children.clear();
    myFiles.clear();
  }

  /**
   * @return iterator of all files.
   */
  Iterator<VirtualFile> getFileIterator(){
    return myFiles.iterator();
  }

  /**
   * @return all files (in depth) located under specified <code>psiDirectory</code>.
   * Please note that returned fiels can be invalid.
   */
  ArrayList<VirtualFile> getFiles(VirtualFile dir){
    ArrayList<VirtualFile> filesList=new ArrayList<VirtualFile>();
    collectFiles(dir,filesList);
    return filesList;
  }

  private void collectFiles(VirtualFile dir,ArrayList<VirtualFile> filesList){
    ArrayList<VirtualFile> children = myDirectory2Children.get(dir);
    if (children!= null) {
      for (VirtualFile child : children) {
        if (!child.isDirectory()) {
          LOG.assertTrue(!filesList.contains(child));
          filesList.add(child);
        } else {
          collectFiles(child, filesList);
        }
      }
    }
  }
}

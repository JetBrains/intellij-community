package com.intellij.openapi.keymap.ex;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;

import java.lang.ref.WeakReference;

public final class WeakKeymapManagerListener implements KeymapManagerListener{
  private KeymapManagerEx myKeymapManager;
  private WeakReference myRef;

  public WeakKeymapManagerListener(KeymapManagerEx keymapManager,KeymapManagerListener delegate){
    myKeymapManager=keymapManager;
    myRef=new WeakReference(delegate);
  }

  public void activeKeymapChanged(Keymap keymap){
    KeymapManagerListener delegate=(KeymapManagerListener)myRef.get();
    if(delegate!=null){
      delegate.activeKeymapChanged(keymap);
    }else{
      myKeymapManager.removeKeymapManagerListener(this);
    }
  }
}

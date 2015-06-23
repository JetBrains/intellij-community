/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.impl.providers.masterKey;

import com.intellij.concurrency.AsyncFutureFactory;
import com.intellij.concurrency.AsyncFutureResult;
import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeTimed;
import com.intellij.ide.passwordSafe.impl.providers.BasePasswordSafeProvider;
import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.ide.passwordSafe.impl.providers.EncryptionUtil;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.windows.WindowsCryptUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * The password safe that stores information in configuration file encrypted by master password
 */
public class MasterKeyPasswordSafe extends BasePasswordSafeProvider {
  private static final String TEST_PASSWORD_KEY = "TEST_PASSWORD:";
  private static final String TEST_PASSWORD_VALUE = "test password";

  private final PasswordDatabase myDatabase;
  private transient final PasswordSafeTimed<Ref<Object>> myKey = new PasswordSafeTimed<Ref<Object>>() {
    protected Ref<Object> compute() {
      return Ref.create();
    }

    @Override
    protected int getMinutesToLive() {
      return Registry.intValue("passwordSafe.masterPassword.ttl");
    }
  };

  public MasterKeyPasswordSafe(PasswordDatabase database) {
    this.myDatabase = database;
  }

  /**
   * Reset password for the password safe (clears password database). The method is used from plugin's UI.
   *
   * @param password the password to set
   * @param encrypt  if the password should be encrypted an stored is master database
   */
  void resetMasterPassword(String password, boolean encrypt) {
    myKey.get().set(EncryptionUtil.genPasswordKey(password));
    myDatabase.clear();
    try {
      storePassword(null, MasterKeyPasswordSafe.class, testKey(password), TEST_PASSWORD_VALUE);
      if (encrypt) {
        myDatabase.setPasswordInfo(encryptPassword(password));
      }
      else {
        myDatabase.setPasswordInfo(ArrayUtil.EMPTY_BYTE_ARRAY);
      }
    }
    catch (PasswordSafeException e) {
      throw new IllegalStateException("There should be no problem with password at this point", e);
    }
  }

  /**
   * Set password to use (used from plugin's UI)
   *
   * @param password the password
   * @return true, if password is a correct one
   */
  boolean setMasterPassword(String password) {
    Object savedKey = myKey.get().get();
    myKey.get().set(EncryptionUtil.genPasswordKey(password));
    String rc;
    try {
      rc = getPassword(null, MasterKeyPasswordSafe.class, testKey(password));
    }
    catch (PasswordSafeException e) {
      throw new IllegalStateException("There should be no problem with password at this point", e);
    }
    if (!TEST_PASSWORD_VALUE.equals(rc)) {
      myKey.get().set(savedKey);
      return false;
    }
    else {
      return true;
    }
  }

  /**
   * Encrypt database with new password
   *
   * @param oldPassword the old password
   * @param newPassword the new password
   * @param encrypt
   * @return re-encrypted database
   */
  boolean changeMasterPassword(String oldPassword, String newPassword, boolean encrypt) {
    if (!setMasterPassword(oldPassword)) {
      return false;
    }
    byte[] oldKey = (byte[])myKey.get().get(); // set right in the previous call
    byte[] newKey = EncryptionUtil.genPasswordKey(newPassword);
    ByteArrayWrapper testKey = new ByteArrayWrapper(EncryptionUtil.dbKey(oldKey, MasterKeyPasswordSafe.class, testKey(oldPassword)));
    HashMap<ByteArrayWrapper, byte[]> oldDb = new HashMap<ByteArrayWrapper, byte[]>();
    myDatabase.copyTo(oldDb);
    HashMap<ByteArrayWrapper, byte[]> newDb = new HashMap<ByteArrayWrapper, byte[]>();
    for (Map.Entry<ByteArrayWrapper, byte[]> e : oldDb.entrySet()) {
      if (testKey.equals(e.getKey())) {
        continue;
      }
      byte[] decryptedKey = EncryptionUtil.decryptKey(oldKey, e.getKey().unwrap());
      String decryptedText = EncryptionUtil.decryptText(oldKey, e.getValue());
      newDb.put(new ByteArrayWrapper(EncryptionUtil.encryptKey(newKey, decryptedKey)), EncryptionUtil.encryptText(newKey, decryptedText));
    }
    synchronized (myDatabase.getDbLock()) {
      resetMasterPassword(newPassword, encrypt);
      myDatabase.putAll(newDb);
    }
    return true;
  }


  private static String testKey(String password) {
    return TEST_PASSWORD_KEY + password;
  }

  @NotNull
  @Override
  protected byte[] key(@Nullable final Project project, @NotNull final Class requestor) throws PasswordSafeException {
    Object key = myKey.get().get();
    if (key instanceof byte[]) return (byte[])key;
    if (key instanceof PasswordSafeException && ((PasswordSafeException)key).justHappened()) throw (PasswordSafeException)key;

    if (isPasswordEncrypted()) {
      try {
        setMasterPassword(decryptPassword(myDatabase.getPasswordInfo()));
        key = myKey.get().get();
        if (key instanceof byte[]) return (byte[])key;
      }
      catch (PasswordSafeException e) {
        // ignore exception and ask password
      }
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      throw new MasterPasswordUnavailableException("The provider is not available in headless environment");
    }

    key = invokeAndWait(new ThrowableComputable<Object, PasswordSafeException>() {
      @Override
      public Object compute() throws PasswordSafeException {
        Object key = myKey.get().get();
        if (key instanceof byte[] || key instanceof PasswordSafeException && ((PasswordSafeException)key).justHappened()) {
          return key;
        }
        try {
          if (myDatabase.isEmpty()) {
            if (!MasterPasswordDialog.resetMasterPasswordDialog(project, MasterKeyPasswordSafe.this, requestor).showAndGet()) {
              throw new MasterPasswordUnavailableException("Master password is required to store passwords in the database.");
            }
          }
          else {
            MasterPasswordDialog.askPassword(project, MasterKeyPasswordSafe.this, requestor);
          }
        }
        catch (PasswordSafeException e) {
          myKey.get().set(e);
          throw e;
        }
        return myKey.get().get();
      }
    }, project == null ? Conditions.alwaysFalse() : project.getDisposed());
    if (key instanceof byte[]) return (byte[])key;
    if (key instanceof PasswordSafeException) throw (PasswordSafeException)key;

    throw new AssertionError();
  }

  private static final Object ourEDTLock = new Object();
  public <T, E extends Throwable> T invokeAndWait(@NotNull final ThrowableComputable<T, E> computable, @NotNull final Condition<?> expired) throws E {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return computable.compute();
    }

    final AsyncFutureResult<Object> future = AsyncFutureFactory.getInstance().createAsyncFutureResult();
    final ExpirableRunnable runnable = new ExpirableRunnable() {
      @Override
      public boolean isExpired() {
        boolean b = expired.value(null);
        if (b) future.setException(new ProcessCanceledException());
        return b;
      }

      @Override
      public void run() {
        try {
          future.set(computable.compute());
        }
        catch (Throwable e) {
          future.setException(e);
        }
      }
    };
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    synchronized (ourEDTLock) {
      if (indicator != null && indicator.isModal()) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!runnable.isExpired()) {
              runnable.run();
            }
          }
        });
      }
      else {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(runnable);
      }
      try {
        return (T)future.get();
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException(e);
      }
      catch (ExecutionException e) {
        throw (E) e.getCause();
      }
    }
  }

  @Override
  public String getPassword(@Nullable Project project, @NotNull Class requestor, String key) throws PasswordSafeException {
    if (myDatabase.isEmpty()) {
      return null;
    }
    return super.getPassword(project, requestor, key);
  }

  @Override
  public void removePassword(@Nullable Project project, @NotNull Class requester, String key) throws PasswordSafeException {
    if (myDatabase.isEmpty()) {
      return;
    }
    super.removePassword(project, requester, key);
  }

  @Override
  protected byte[] getEncryptedPassword(byte[] key) {
    return myDatabase.get(key);
  }

  @Override
  protected void removeEncryptedPassword(byte[] key) {
    myDatabase.remove(key);
  }

  @Override
  protected void storeEncryptedPassword(byte[] key, byte[] encryptedPassword) {
    myDatabase.put(key, encryptedPassword);
  }

  @Override
  public boolean isSupported() {
    return !ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  @Override
  public String getDescription() {
    return "This provider stores passwords in IDEA config and uses master password to encrypt other passwords. " +
           "The passwords for the same resources are shared between different projects.";
  }

  @Override
  public String getName() {
    return "Master Key PasswordSafe";
  }


  public boolean isMasterPasswordEnabled() {
    return setMasterPassword("");
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  public boolean isOsProtectedPasswordSupported() {
    // TODO extension point needed?
    return SystemInfo.isWindows;
  }


  /**
   * Encrypt master password
   *
   * @param pw the password to encrypt
   * @return the encrypted password
   * @throws MasterPasswordUnavailableException
   *          if encryption fails
   */
  private static byte[] encryptPassword(String pw) throws MasterPasswordUnavailableException {
    assert SystemInfo.isWindows;
    return WindowsCryptUtils.protect(EncryptionUtil.getUTF8Bytes(pw));
  }

  /**
   * Decrypt master password
   *
   * @param pw the password to decrypt
   * @return the decrypted password
   * @throws MasterPasswordUnavailableException
   *          if decryption fails
   */
  private static String decryptPassword(byte[] pw) throws MasterPasswordUnavailableException {
    if (!SystemInfo.isWindows) throw new AssertionError("Windows OS expected");

    return new String(WindowsCryptUtils.unprotect(pw), CharsetToolkit.UTF8_CHARSET);
  }

  public boolean isPasswordEncrypted() {
    if (!isOsProtectedPasswordSupported()) return false;

    byte[] info = myDatabase.getPasswordInfo();
    return info != null && info.length > 0;
  }

  public boolean isEmpty() {
    return myDatabase.isEmpty();
  }
}

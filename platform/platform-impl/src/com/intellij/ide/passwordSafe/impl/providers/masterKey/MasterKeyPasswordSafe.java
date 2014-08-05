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

import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.intellij.ide.passwordSafe.PasswordSafeException;
import com.intellij.ide.passwordSafe.impl.PasswordSafeTimed;
import com.intellij.ide.passwordSafe.impl.providers.BasePasswordSafeProvider;
import com.intellij.ide.passwordSafe.impl.providers.ByteArrayWrapper;
import com.intellij.ide.passwordSafe.impl.providers.EncryptionUtil;
import com.intellij.ide.passwordSafe.impl.providers.masterKey.windows.WindowsCryptUtils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The password safe that stores information in configuration file encrypted by master password
 */
public class MasterKeyPasswordSafe extends BasePasswordSafeProvider {
  private static final String TEST_PASSWORD_KEY = "TEST_PASSWORD:";
  private static final String TEST_PASSWORD_VALUE = "test password";
  final PasswordDatabase database;
  /**
   * The key to use to encrypt data
   */
  private transient final PasswordSafeTimed<AtomicReference<byte[]>> key = new PasswordSafeTimed<AtomicReference<byte[]>>() {
    protected AtomicReference<byte[]> compute() {
      return new AtomicReference<byte[]>();
    }

    @Override
    protected int getMinutesToLive() {
      return Registry.intValue("passwordSafe.masterPassword.ttl");
    }
  };

  public MasterKeyPasswordSafe(PasswordDatabase database) {
    this.database = database;
  }

  protected boolean isTestMode() {
    return false;
  }

  /**
   * Reset password for the password safe (clears password database). The method is used from plugin's UI.
   *
   * @param password the password to set
   * @param encrypt  if the password should be encrypted an stored is master database
   */
  void resetMasterPassword(String password, boolean encrypt) {
    key.get().set(EncryptionUtil.genPasswordKey(password));
    database.clear();
    try {
      storePassword(null, MasterKeyPasswordSafe.class, testKey(password), TEST_PASSWORD_VALUE);
      if (encrypt) {
        database.setPasswordInfo(encryptPassword(password));
      }
      else {
        database.setPasswordInfo(ArrayUtil.EMPTY_BYTE_ARRAY);
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
    byte[] savedKey = key.get().get();
    key.get().set(EncryptionUtil.genPasswordKey(password));
    String rc;
    try {
      rc = getPassword(null, MasterKeyPasswordSafe.class, testKey(password));
    }
    catch (PasswordSafeException e) {
      throw new IllegalStateException("There should be no problem with password at this point", e);
    }
    if (!TEST_PASSWORD_VALUE.equals(rc)) {
      key.get().set(savedKey);
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
    byte[] oldKey = key.get().get(); // set right in the previous call
    byte[] newKey = EncryptionUtil.genPasswordKey(newPassword);
    ByteArrayWrapper testKey = new ByteArrayWrapper(EncryptionUtil.dbKey(oldKey, MasterKeyPasswordSafe.class, testKey(oldPassword)));
    HashMap<ByteArrayWrapper, byte[]> oldDb = new HashMap<ByteArrayWrapper, byte[]>();
    database.copyTo(oldDb);
    HashMap<ByteArrayWrapper, byte[]> newDb = new HashMap<ByteArrayWrapper, byte[]>();
    for (Map.Entry<ByteArrayWrapper, byte[]> e : oldDb.entrySet()) {
      if (testKey.equals(e.getKey())) {
        continue;
      }
      byte[] decryptedKey = EncryptionUtil.decryptKey(oldKey, e.getKey().unwrap());
      String decryptedText = EncryptionUtil.decryptText(oldKey, e.getValue());
      newDb.put(new ByteArrayWrapper(EncryptionUtil.encryptKey(newKey, decryptedKey)), EncryptionUtil.encryptText(newKey, decryptedText));
    }
    synchronized (database.getDbLock()) {
      resetMasterPassword(newPassword, encrypt);
      database.putAll(newDb);
    }
    return true;
  }


  private static String testKey(String password) {
    return TEST_PASSWORD_KEY + password;
  }

  @Override
  protected byte[] key(@Nullable final Project project, @NotNull final Class requestor) throws PasswordSafeException {
    Application application = ApplicationManager.getApplication();
    if (!isTestMode() && application.isHeadlessEnvironment()) {
      throw new MasterPasswordUnavailableException("The provider is not available in headless environment");
    }
    final Ref<byte[]> result = Ref.create(key.get().get());
    if (result.isNull()) {
      if (isPasswordEncrypted()) {
        try {
          setMasterPassword(decryptPassword(database.getPasswordInfo()));
          result.set(key.get().get());
        }
        catch (PasswordSafeException e) {
          // ignore exception and ask password
        }
      }
      if (result.isNull()) {
        final Ref<PasswordSafeException> ex = new Ref<PasswordSafeException>();
        application.invokeAndWait(new Runnable() {
          public void run() {
            result.set(key.get().get());
            if (result.isNull()) {
              try {
                if (isTestMode()) {
                  throw new MasterPasswordUnavailableException("Master password must be specified in test mode.");
                }
                if (database.isEmpty()) {
                  if (!MasterPasswordDialog.resetMasterPasswordDialog(project, MasterKeyPasswordSafe.this, requestor).showAndGet()) {
                    throw new MasterPasswordUnavailableException("Master password is required to store passwords in the database.");
                  }
                }
                else {
                  MasterPasswordDialog.askPassword(project, MasterKeyPasswordSafe.this, requestor);
                }
                result.set(key.get().get());
              }
              catch (PasswordSafeException e) {
                ex.set(e);
              }
              catch (Exception e) {
                //noinspection ThrowableInstanceNeverThrown
                ex.set(new MasterPasswordUnavailableException("The problem with retrieving the password", e));
              }
            }
          }
        }, ModalityState.any());
        //noinspection ThrowableResultOfMethodCallIgnored
        if (ex.get() != null) {
          throw ex.get();
        }
      }
    }
    return result.get();
  }

  @Override
  public String getPassword(@Nullable Project project, @NotNull Class requestor, String key) throws PasswordSafeException {
    if (database.isEmpty()) {
      return null;
    }
    return super.getPassword(project, requestor, key);
  }

  @Override
  public void removePassword(@Nullable Project project, @NotNull Class requester, String key) throws PasswordSafeException {
    if (database.isEmpty()) {
      return;
    }
    super.removePassword(project, requester, key);
  }

  @Override
  protected byte[] getEncryptedPassword(byte[] key) {
    return database.get(key);
  }

  @Override
  protected void removeEncryptedPassword(byte[] key) {
    database.remove(key);
  }

  @Override
  protected void storeEncryptedPassword(byte[] key, byte[] encryptedPassword) {
    database.put(key, encryptedPassword);
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

    try {
      return new String(WindowsCryptUtils.unprotect(pw), "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 not available", e);
    }
  }

  public boolean isPasswordEncrypted() {
    if (!isOsProtectedPasswordSupported()) return false;

    byte[] i = database.getPasswordInfo();
    return i != null && i.length > 0;
  }

  public boolean isEmpty() {
    return database.isEmpty();
  }
}

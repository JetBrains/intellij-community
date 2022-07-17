// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ssh.SSHUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

import static com.intellij.credentialStore.CredentialAttributesKt.generateServiceName;
import static com.intellij.externalProcessAuthHelper.AuthenticationMode.FULL;

public class NativeSshGuiAuthenticator implements NativeSshAuthenticator {
  @NotNull private final Project myProject;
  @NotNull private final AuthenticationGate myAuthenticationGate;
  @NotNull private final AuthenticationMode myAuthenticationMode;
  private final boolean myDoNotRememberPasswords;

  @Nullable private String myLastAskedKeyPath = null;
  @Nullable private String myLastAskedUserName = null;
  @Nullable private String myLastAskedConfirmationInput = null;

  public NativeSshGuiAuthenticator(@NotNull Project project,
                                   @NotNull AuthenticationGate authenticationGate,
                                   @NotNull AuthenticationMode authenticationMode,
                                   boolean doNotRememberPasswords) {
    myProject = project;
    myAuthenticationGate = authenticationGate;
    myAuthenticationMode = authenticationMode;
    myDoNotRememberPasswords = doNotRememberPasswords;
  }

  @Nullable
  @Override
  public String handleInput(@NotNull @NlsSafe String description) {
    if(myAuthenticationMode == AuthenticationMode.NONE) return null;
    return myAuthenticationGate.waitAndCompute(() -> {
      if (isKeyPassphrase(description)) return askKeyPassphraseInput(description);
      if (isSshPassword(description)) return askSshPasswordInput(description);
      if (isConfirmation(description)) return askConfirmationInput(description);

      return askGenericInput(description);
    });
  }


  private static boolean isKeyPassphrase(@NotNull String description) {
    return SSHUtil.PASSPHRASE_PROMPT.matcher(description).matches();
  }

  @Nullable
  private String askKeyPassphraseInput(@NotNull String description) {
    Matcher matcher = SSHUtil.PASSPHRASE_PROMPT.matcher(description);
    if (!matcher.matches()) throw new IllegalStateException(description);
    String keyPath = SSHUtil.extractKeyPath(matcher);

    boolean resetPassword = keyPath.equals(myLastAskedKeyPath);
    myLastAskedKeyPath = keyPath;

    if (myDoNotRememberPasswords) {
      return askUser(() -> {
        String message = ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath));
        return Messages.showPasswordDialog(myProject, message, ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.title"), null);
      });
    }
    else {
      return askPassphrase(myProject, keyPath, resetPassword, myAuthenticationMode);
    }
  }

  private static boolean isSshPassword(@NotNull String description) {
    return SSHUtil.PASSWORD_PROMPT.matcher(description).matches();
  }

  @Nullable
  private String askSshPasswordInput(@NotNull String description) {
    Matcher matcher = SSHUtil.PASSWORD_PROMPT.matcher(description);
    if (!matcher.matches()) throw new IllegalStateException(description);
    String username = SSHUtil.extractUsername(matcher);

    boolean resetPassword = username.equals(myLastAskedUserName);
    myLastAskedUserName = username;

    if (myDoNotRememberPasswords) {
      return askUser(() -> {
        String message = ExternalProcessAuthHelperBundle.message("ssh.password.message", username);
        return Messages.showPasswordDialog(myProject, message, ExternalProcessAuthHelperBundle.message("ssh.password.title"), null);
      });
    }
    else {
      return askPassword(myProject, username, resetPassword, myAuthenticationMode);
    }
  }

  private static boolean isConfirmation(@NotNull @NlsSafe String description) {
    return description.contains(SSHUtil.CONFIRM_CONNECTION_PROMPT);
  }

  @Nullable
  private String askConfirmationInput(@NotNull @NlsSafe String description) {
    return askUser(() -> {
      @NlsSafe String message = StringUtil.replace(description,
                                                   SSHUtil.CONFIRM_CONNECTION_PROMPT + " (yes/no)?",
                                                   SSHUtil.CONFIRM_CONNECTION_PROMPT + "?");

      String knownAnswer = myAuthenticationGate.getSavedInput(message);
      if (knownAnswer != null && myLastAskedConfirmationInput == null) {
        myLastAskedConfirmationInput = knownAnswer;
        return knownAnswer;
      }

      int answer = Messages.showYesNoDialog(myProject, message, ExternalProcessAuthHelperBundle.message("title.ssh.confirmation"), null);
      String textAnswer;
      if (answer == Messages.YES) {
        textAnswer = "yes";
      }
      else if (answer == Messages.NO) {
        textAnswer = "no";
      }
      else {
        throw new AssertionError(answer);
      }
      myAuthenticationGate.saveInput(message, textAnswer);
      return textAnswer;
    });
  }

  @Nullable
  private String askGenericInput(@NotNull @Nls String description) {
    return askUser(() -> Messages.showPasswordDialog(myProject, description, ExternalProcessAuthHelperBundle.message("ssh.keyboard.interactive.title"), null));
  }

  @Nullable
  private String askUser(@NotNull Computable<String> query) {
    if (myAuthenticationMode != FULL) return null;

    Ref<String> answerRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> answerRef.set(query.compute()), ModalityState.any());
    return answerRef.get();
  }

  @NonNls
  @Nullable
  private static String askPassphrase(@Nullable Project project,
                                      @NotNull @NlsSafe String keyPath,
                                      boolean resetPassword,
                                      @NotNull AuthenticationMode authenticationMode) {
    if (authenticationMode == AuthenticationMode.NONE) return null;
    CredentialAttributes newAttributes = passphraseCredentialAttributes(keyPath);
    Credentials credentials = PasswordSafe.getInstance().get(newAttributes);
    if (credentials != null && !resetPassword) {
      String password = credentials.getPasswordAsString();
      if (password != null && !password.isEmpty()) return password;
    }
    if (authenticationMode == AuthenticationMode.SILENT) return null;
    return CredentialPromptDialog.askPassword(project,
                                              ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.title"),
                                              ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath)),
                                              newAttributes, true);
  }

  @NonNls
  @Nullable
  private static String askPassword(@Nullable Project project,
                                    @NotNull @NlsSafe String username,
                                    boolean resetPassword,
                                    @NotNull AuthenticationMode authenticationMode) {
    if (authenticationMode == AuthenticationMode.NONE) return null;
    CredentialAttributes newAttributes = passwordCredentialAttributes(username);
    Credentials credentials = PasswordSafe.getInstance().get(newAttributes);
    if (credentials != null && !resetPassword) {
      String password = credentials.getPasswordAsString();
      if (password != null) return password;
    }
    if (authenticationMode == AuthenticationMode.SILENT) return null;
    return CredentialPromptDialog.askPassword(project,
                                              ExternalProcessAuthHelperBundle.message("ssh.password.title"),
                                              ExternalProcessAuthHelperBundle.message("ssh.password.message", username),
                                              newAttributes, true);
  }

  @NotNull
  private static CredentialAttributes passphraseCredentialAttributes(@NotNull @Nls String key) {
    return new CredentialAttributes(generateServiceName(ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.passphrase"), key), key);
  }

  @NotNull
  private static CredentialAttributes passwordCredentialAttributes(@NotNull @Nls String key) {
    return new CredentialAttributes(generateServiceName(ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.password"), key), key);
  }
}

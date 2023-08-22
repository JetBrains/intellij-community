// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

import com.google.protobuf.ByteString
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

fun PublicKey.rsaEncrypt(rawToken: ByteString): ByteString = rawToken.performRsaTransformation(Cipher.ENCRYPT_MODE, this)
fun PrivateKey.rsaDecrypt(encryptedToken: ByteString): ByteString = encryptedToken.performRsaTransformation(Cipher.DECRYPT_MODE, this)

private fun ByteString.performRsaTransformation(mode: Int, key: Key): ByteString {
  return toByteArray().performRsaTransformation(mode, key).let(ByteString::copyFrom)
}

private fun ByteArray.performRsaTransformation(mode: Int, key: Key): ByteArray {
  return Cipher.getInstance("RSA/ECB/PKCS1Padding").apply {
    init(mode, key)
  }.doFinal(this)
}

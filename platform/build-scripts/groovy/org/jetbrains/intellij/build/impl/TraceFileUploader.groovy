// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class TraceFileUploader {
    private final String myServerUrl

    private static final String UTF_8 = "UTF-8"
    private static final int MB = 1024 * 1024

    static class UploadException extends Exception {
        UploadException(String message) {
            super(message)
        }

        UploadException(String message, Throwable cause) {
            super(message, cause)
        }
    }

    TraceFileUploader(@NotNull String serverUrl) {
        myServerUrl = fixServerUrl(serverUrl)
    }

    protected void log(String message) {
    }

    void upload(@NotNull final File file, @NotNull final Map<String, String> metadata) throws UploadException {
        log("Preparing to upload " + file + " to " + myServerUrl)

        if (!file.exists()) {
            throw new UploadException("The file " + file.getPath() + " does not exist")
        }

        final String id = uploadMetadata(getFullMetadata(file, metadata))
        log("Performed metadata upload. Import id is: " + id)

        final String response = uploadFile(file, id)
        log("Performed file upload. Server answered: " + response)
    }

    @NotNull
    protected static Map<String, String> getFullMetadata(@NotNull File file, @NotNull Map<String, String> metadata) {
        final Map<String, String> map = new LinkedHashMap<>(metadata)
        map.put("internal.upload.file.name", file.getName())
        map.put("internal.upload.file.path", file.getAbsolutePath())
        map.put("internal.upload.file.size", String.valueOf(file.length()))
        return map
    }

    @NotNull
    private String uploadMetadata(@NotNull Map<String, String> metadata) throws UploadException {
        try {
            String postUrl = myServerUrl + "import"
            log("Posting to url " + postUrl)

            HttpURLConnection conn = (HttpURLConnection) new URL(postUrl).openConnection()
            conn.setDoInput(true)
            conn.setDoOutput(true)
            conn.setUseCaches(false)
            conn.setInstanceFollowRedirects(true)
            conn.setRequestMethod("POST")

            final String metadataContent = JsonOutput.toJson(metadata)
            log("Uploading metadata: " + metadataContent)
            final byte[] content = metadataContent.getBytes(UTF_8)

            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Accept", "text/plain;charset=UTF-8")
            conn.setRequestProperty("Accept-Charset", UTF_8)
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            conn.setRequestProperty("Content-Length", String.valueOf(content.length))
            conn.setFixedLengthStreamingMode(content.length)

            InputStream is = new ByteArrayInputStream(content)
            OutputStream output = conn.getOutputStream()
            transfer(is, output, MB)
            is.close()
            output.close()

            // Get the response
            final int code = conn.getResponseCode()
            if (code == 200 || code == 201 || code == 202 || code == 204) {
                return readPlainMetadata(conn)
            } else {
                throw readError(conn, code)
            }
        } catch (Exception e) {
            if (e instanceof UploadException) throw (UploadException) e
            throw new UploadException("Failed to post metadata: " + e.getMessage(), e)
        }
    }

    @NotNull
    private String uploadFile(File file, String id) throws UploadException {
        try {
            String postUrl = myServerUrl + "import/" + URLEncoder.encode(id, UTF_8) + "/upload/tr-single"
            log("Posting to url " + postUrl)

            HttpURLConnection conn = (HttpURLConnection) new URL(postUrl).openConnection()
            conn.setDoInput(true)
            conn.setDoOutput(true)
            conn.setUseCaches(false)
            conn.setRequestMethod("POST")

            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Accept-Charset", UTF_8)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Content-Length", String.valueOf(file.length()))
            conn.setFixedLengthStreamingMode(file.length())

            InputStream is = new BufferedInputStream(new FileInputStream(file), 5 * MB)
            OutputStream output = conn.getOutputStream()
            transfer(is, output, MB)
            is.close()
            output.close()

            // Get the response
            return readBody(conn)
        } catch (Exception e) {
            throw new UploadException("Failed to upload file: " + e.getMessage(), e)
        }
    }

    @NotNull
    private static String readBody(HttpURLConnection conn) throws IOException {
        final InputStream response = conn.getInputStream()
        final ByteArrayOutputStream output = new ByteArrayOutputStream()
        transfer(response, output, 8 * 1024)
        response.close()
        return new String(output.toByteArray(), UTF_8)
    }

    private static UploadException readError(HttpURLConnection conn, int code) throws IOException {
        final String body = readBody(conn)
        return new UploadException("Unexpected code from server: " + code + " body:" + body)
    }

    private static String readPlainMetadata(@NotNull final HttpURLConnection conn) throws IOException, UploadException {
        final String body = readBody(conn).trim()
        if (body.startsWith('{')) {
            def object = new JsonSlurper().parseText(body)
            assert object instanceof Map
            return object['id']
        }
        try {
            return String.valueOf(Long.parseLong(body))
        } catch (NumberFormatException ignored) {
        }
        throw new UploadException("Server returned neither import json nor id: " + body)
    }

    private static String fixServerUrl(String serverUrl) {
        String url = serverUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url
        }
        if (!url.endsWith("/")) url += '/'
        return url
    }

    private static void transfer(InputStream is, OutputStream output, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize]
        int length
        while ((length = is.read(buffer)) > 0) {
            output.write(buffer, 0, length)
        }
        output.flush()
    }
}

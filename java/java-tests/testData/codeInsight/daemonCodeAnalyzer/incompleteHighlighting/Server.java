/*
 * Copyright 2013 Haulmont
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * <info descr="Open in browser (Ctrl+Click, Ctrl+B)">http://www.apache.org/licenses/LICENSE-2.0</info>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.haulmont.yarg.server;

import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">console</info>.<info descr="Not resolved until the project is fully loaded">ReportEngineCreator</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">reporting</info>.<info descr="Not resolved until the project is fully loaded">ReportOutputDocument</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">reporting</info>.<info descr="Not resolved until the project is fully loaded">Reporting</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">reporting</info>.<info descr="Not resolved until the project is fully loaded">RunParams</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">structure</info>.<info descr="Not resolved until the project is fully loaded">Report</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">structure</info>.<info descr="Not resolved until the project is fully loaded">ReportParameter</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">structure</info>.<info descr="Not resolved until the project is fully loaded">xml</info>.<info descr="Not resolved until the project is fully loaded">XmlReader</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">structure</info>.<info descr="Not resolved until the project is fully loaded">xml</info>.<info descr="Not resolved until the project is fully loaded">impl</info>.<info descr="Not resolved until the project is fully loaded">DefaultXmlReader</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">util</info>.<info descr="Not resolved until the project is fully loaded">converter</info>.<info descr="Not resolved until the project is fully loaded">ObjectToStringConverter</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">util</info>.<info descr="Not resolved until the project is fully loaded">converter</info>.<info descr="Not resolved until the project is fully loaded">ObjectToStringConverterImpl</info>;
import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">haulmont</info>.<info descr="Not resolved until the project is fully loaded">yarg</info>.<info descr="Not resolved until the project is fully loaded">util</info>.<info descr="Not resolved until the project is fully loaded">properties</info>.<info descr="Not resolved until the project is fully loaded">PropertiesLoader</info>;
import org.apache.<info descr="Not resolved until the project is fully loaded">commons</info>.<info descr="Not resolved until the project is fully loaded">io</info>.<info descr="Not resolved until the project is fully loaded">FileUtils</info>;
import org.apache.<info descr="Not resolved until the project is fully loaded">commons</info>.<info descr="Not resolved until the project is fully loaded">lang3</info>.<info descr="Not resolved until the project is fully loaded">StringUtils</info>;
import org.<info descr="Not resolved until the project is fully loaded">slf4j</info>.<info descr="Not resolved until the project is fully loaded">Logger</info>;
import org.<info descr="Not resolved until the project is fully loaded">slf4j</info>.<info descr="Not resolved until the project is fully loaded">LoggerFactory</info>;
import <info descr="Not resolved until the project is fully loaded">spark</info>.<info descr="Not resolved until the project is fully loaded">QueryParamsMap</info>;
import <info descr="Not resolved until the project is fully loaded">spark</info>.<info descr="Not resolved until the project is fully loaded">Request</info>;
import <info descr="Not resolved until the project is fully loaded">spark</info>.<info descr="Not resolved until the project is fully loaded">Response</info>;
import <info descr="Not resolved until the project is fully loaded">spark</info>.<info descr="Not resolved until the project is fully loaded">Spark</info>;

import <info descr="Not resolved until the project is fully loaded">javax</info>.<info descr="Not resolved until the project is fully loaded">servlet</info>.<info descr="Not resolved until the project is fully loaded">http</info>.<info descr="Not resolved until the project is fully loaded">HttpServletResponse</info>;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static <info descr="Not resolved until the project is fully loaded">spark</info>.<info descr="Not resolved until the project is fully loaded">Spark</info>.<info descr="Not resolved until the project is fully loaded">get</info>;
import static <info descr="Not resolved until the project is fully loaded">spark</info>.<info descr="Not resolved until the project is fully loaded">Spark</info>.<info descr="Not resolved until the project is fully loaded">internalServerError</info>;

public class Server {
  protected String reportsPath;
  protected Integer port;
  protected <info descr="Not resolved until the project is fully loaded">PropertiesLoader</info> propertiesLoader;

  protected static <info descr="Not resolved until the project is fully loaded">ObjectToStringConverter</info> converter = new <info descr="Not resolved until the project is fully loaded">ObjectToStringConverterImpl</info>();
  protected <info descr="Not resolved until the project is fully loaded">Logger</info> logger = <info descr="Not resolved until the project is fully loaded">LoggerFactory</info>.<info descr="Not resolved until the project is fully loaded">getLogger</info>(getClass());

  public Server reportsPath(String reportsPath) {
    this.reportsPath = reportsPath;
    return this;
  }

  public Server port(int port) {
    this.port = port;
    return this;
  }

  public Server propertiesLoader(<info descr="Not resolved until the project is fully loaded">PropertiesLoader</info> propertiesLoader) {
    this.propertiesLoader = propertiesLoader;
    return this;
  }

  public void init() throws IOException {
    if (port != null) {
      <info descr="Not resolved until the project is fully loaded">Spark</info>.<info descr="Not resolved until the project is fully loaded">port</info>(port);
    }

    initPing();

    initGenerate();
  }

  public void stop() {
    <info descr="Not resolved until the project is fully loaded">Spark</info>.<info descr="Not resolved until the project is fully loaded">stop</info>();
  }

  protected void initPing() {
    <info descr="Not resolved until the project is fully loaded">get</info>("/ping", (req, res) -> "pong");
  }

  protected void initGenerate() throws IOException {
    <info descr="Not resolved until the project is fully loaded">Reporting</info> reporting = new <info descr="Not resolved until the project is fully loaded">ReportEngineCreator</info>().<info descr="Not resolved until the project is fully loaded">createReportingEngine</info>(propertiesLoader);

    <info descr="Not resolved until the project is fully loaded">get</info>("/generate", (req, res) -> {
      try {
        <info descr="Not resolved until the project is fully loaded">Report</info> report = loadReport(req);
        if (report == null) {
          res.<info descr="Not resolved until the project is fully loaded">type</info>("application/json");
          res.<info descr="Not resolved until the project is fully loaded">status</info>(400);
          return "{\"errorMessage\": " +
                 "\"Report name is not provided or could not find the report.\"}";
        }

        Map<String, Object> params = parseParameters(req, report);
        String templateCode = req.<info descr="Not resolved until the project is fully loaded">queryParams</info>("templateCode");
        <info descr="Not resolved until the project is fully loaded">RunParams</info> reportParams = new <info descr="Not resolved until the project is fully loaded">RunParams</info>(report).<info descr="Not resolved until the project is fully loaded">params</info>(params);
        if (<info descr="Not resolved until the project is fully loaded">StringUtils</info>.<info descr="Not resolved until the project is fully loaded">isNotBlank</info>(templateCode)) {
          reportParams.<info descr="Not resolved until the project is fully loaded">templateCode</info>(templateCode);
        }
        <info descr="Not resolved until the project is fully loaded">ReportOutputDocument</info> reportOutputDocument = reporting.<info descr="Not resolved until the project is fully loaded">runReport</info>(reportParams);
        writeResult(res, reportOutputDocument);
        return "Ok";
      } catch (Exception e) {
        logger.<info descr="Not resolved until the project is fully loaded">error</info>(String.format("An error occurred while generating report [%s]", req.<info descr="Not resolved until the project is fully loaded">queryParams</info>("report")), e);
        throw new RuntimeException(e);
      }
    });

    <info descr="Not resolved until the project is fully loaded">internalServerError</info>((req, res) -> {
      res.<info descr="Not resolved until the project is fully loaded">type</info>("application/json");
      res.<info descr="Not resolved until the project is fully loaded">status</info>(500);
      return "{\"errorMessage\": " +
             "\"An exception occurred while generating the report. Please see the server logs for the detailed information.\"}";
    });
  }

  protected <info descr="Not resolved until the project is fully loaded">Report</info> loadReport(<info descr="Not resolved until the project is fully loaded">Request</info> req) throws IOException {
    String reportName = req.<info descr="Not resolved until the project is fully loaded">queryParams</info>("report");
    if (<info descr="Not resolved until the project is fully loaded">StringUtils</info>.<info descr="Not resolved until the project is fully loaded">isBlank</info>(reportName)) {
      return null;
    } else {
      <info descr="Not resolved until the project is fully loaded">XmlReader</info> xmlReader = new <info descr="Not resolved until the project is fully loaded">DefaultXmlReader</info>();
      return xmlReader.<info descr="Not resolved until the project is fully loaded">parseXml</info>(<info descr="Not resolved until the project is fully loaded">FileUtils</info>.<info descr="Not resolved until the project is fully loaded">readFileToString</info>(new File(String.format("%s/%s.xml", reportsPath, reportName))));
    }
  }

  protected Map<String, Object> parseParameters(<info descr="Not resolved until the project is fully loaded">Request</info> req, <info descr="Not resolved until the project is fully loaded">Report</info> report) {
    <info descr="Not resolved until the project is fully loaded">QueryParamsMap</info> queryParams = req.<info descr="Not resolved until the project is fully loaded">queryMap</info>("params");
    Map<String, Object> params = new HashMap<>();

    for (<info descr="Not resolved until the project is fully loaded">ReportParameter</info> reportParameter : report.<info descr="Not resolved until the project is fully loaded">getReportParameters</info>()) {
      java.lang.String paramValueStr = queryParams.<info descr="Not resolved until the project is fully loaded">value</info>(reportParameter.<info descr="Not resolved until the project is fully loaded">getAlias</info>());
      if (paramValueStr != null) {
        params.put(reportParameter.<info descr="Not resolved until the project is fully loaded">getAlias</info>(),
                   converter.<info descr="Not resolved until the project is fully loaded">convertFromString</info>(reportParameter.<info descr="Not resolved until the project is fully loaded">getParameterClass</info>(), paramValueStr));
      }
    }

    return params;
  }

  protected void writeResult(<info descr="Not resolved until the project is fully loaded">Response</info> res, <info descr="Not resolved until the project is fully loaded">ReportOutputDocument</info> reportOutputDocument) throws IOException {
    <info descr="Not resolved until the project is fully loaded">HttpServletResponse</info> raw = res.<info descr="Not resolved until the project is fully loaded">raw</info>();
    raw.<info descr="Not resolved until the project is fully loaded">setHeader</info>("Content-Disposition", String.format("attachment; filename=\"%s\"", reportOutputDocument.<info descr="Not resolved until the project is fully loaded">getDocumentName</info>()));
    raw.<info descr="Not resolved until the project is fully loaded">setContentLength</info>(reportOutputDocument.<info descr="Not resolved until the project is fully loaded">getContent</info>().<info descr="Not resolved until the project is fully loaded">length</info>);
    raw.<info descr="Not resolved until the project is fully loaded">getOutputStream</info>().<info descr="Not resolved until the project is fully loaded">write</info>(reportOutputDocument.<info descr="Not resolved until the project is fully loaded">getContent</info>());
    res.<info descr="Not resolved until the project is fully loaded">status</info>(200);
  }
}

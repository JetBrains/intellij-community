// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.services.artifactory;

import org.jvnet.ws.wadl.util.DSDispatcher;
import org.jvnet.ws.wadl.util.UriBuilder;

import javax.activation.DataSource;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Endpoint {

  public static DataSource getArtifactInfoByUri(String uri) throws IOException {
    DSDispatcher _dsDispatcher = new DSDispatcher();
    UriBuilder _uriBuilder = new UriBuilder();
    _uriBuilder.addPathSegment(uri);
    String _url = _uriBuilder.buildUri(Collections.emptyMap(), Collections.emptyMap());
    DataSource _retVal =
      _dsDispatcher.doGET(_url, Collections.emptyMap(), "application/vnd.org.jfrog.artifactory.search.ArtifactSearchResult+json");
    return _retVal;
  }

  public static class Search {

        public static class Artifact {

            private final DSDispatcher _dsDispatcher;
            private final UriBuilder _uriBuilder;
            private final HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Artifact(final String url)
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("search");
                _matrixParamSet = _uriBuilder.addPathSegment("artifact");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource getArtifactSearchResultJson(String name, String repos)
                throws IOException {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                _queryParameterValues.put("name", name);
                _queryParameterValues.put("repos", repos);
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.search.ArtifactSearchResult+json");
                return _retVal;
            }

        }

        public static class Gavc {

            private final DSDispatcher _dsDispatcher;
            private final UriBuilder _uriBuilder;
            private final HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Gavc(final String url)
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("search");
                _matrixParamSet = _uriBuilder.addPathSegment("gavc");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource getGavcSearchResultJson(String g, String a, String v, String c, String repos)
                throws IOException {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                _queryParameterValues.put("g", g);
                _queryParameterValues.put("a", a);
                _queryParameterValues.put("v", v);
                _queryParameterValues.put("c", c);
                _queryParameterValues.put("repos", repos);
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.search.GavcSearchResult+json");
                return _retVal;
            }

        }
        public static class Archive {

            private final DSDispatcher _dsDispatcher;
            private final UriBuilder _uriBuilder;
            private final HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Archive(final String url)
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("search");
                _matrixParamSet = _uriBuilder.addPathSegment("archive");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource getArchiveSearchResultJson(String className, String repos)
                throws IOException {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                _queryParameterValues.put("name", className);
                _queryParameterValues.put("repos", repos);
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.search.ArchiveEntrySearchResult+json");
                return _retVal;
            }

        }

    }

    public static class System {

        private final DSDispatcher _dsDispatcher;
        private final UriBuilder _uriBuilder;
        private final HashMap<String, Object> _templateAndMatrixParameterValues;

        /**
         * Create new instance
         *
         * @param url
         */
        public System(final String url)
        {
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment(url);
            _matrixParamSet = _uriBuilder.addPathSegment("system");
            _templateAndMatrixParameterValues = new HashMap<>();
        }

        public DataSource getAsApplicationXml()
            throws IOException {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public static class Configuration {

            private final DSDispatcher _dsDispatcher;
            private final UriBuilder _uriBuilder;
            private final HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Configuration(final String url)
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("system");
                _matrixParamSet = _uriBuilder.addPathSegment("configuration");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource postAsTextPlain(DataSource input)
                throws IOException {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doPOST(input, "application/xml", _url, _headerParameterValues, "text/plain");
                return _retVal;
            }

            public DataSource getAsApplicationXml()
                throws IOException {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
                return _retVal;
            }

            public static class RemoteRepositories {

                private final DSDispatcher _dsDispatcher;
                private final UriBuilder _uriBuilder;
                private final HashMap<String, Object> _templateAndMatrixParameterValues;

                /**
                 * Create new instance
                 *
                 * @param url
                 */
                public RemoteRepositories(final String url)
                {
                    _dsDispatcher = new DSDispatcher();
                    _uriBuilder = new UriBuilder();
                    List<String> _matrixParamSet;
                    _matrixParamSet = _uriBuilder.addPathSegment(url);
                    _matrixParamSet = _uriBuilder.addPathSegment("system");
                    _matrixParamSet = _uriBuilder.addPathSegment("configuration");
                    _matrixParamSet = _uriBuilder.addPathSegment("remoteRepositories");
                    _templateAndMatrixParameterValues = new HashMap<>();
                }

                public void put(DataSource input)
                    throws IOException {
                    HashMap<String, Object> _queryParameterValues = new HashMap<>();
                    HashMap<String, Object> _headerParameterValues = new HashMap<>();
                    String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                    DataSource _retVal = _dsDispatcher.doPUT(input, "application/xml", _url, _headerParameterValues, null);
                    return ;
                }

            }

        }

    }

    public static class SystemVersion {

        private final DSDispatcher _dsDispatcher;
        private final UriBuilder _uriBuilder;
        private final HashMap<String, Object> _templateAndMatrixParameterValues;

        /**
         * Create new instance
         *
         * @param url
         */
        public SystemVersion(final String url)
        {
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment(url);
            _matrixParamSet = _uriBuilder.addPathSegment("system/version");
            _templateAndMatrixParameterValues = new HashMap<>();
        }

        public DataSource getSystemVersionJson()
            throws IOException {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.system.Version+json");
            return _retVal;
        }

    }

  /**
  * @author Gregory.Shrago
  */
  public static class Repositories {

      private final DSDispatcher _dsDispatcher;
      private final UriBuilder _uriBuilder;
      private final HashMap<String, Object> _templateAndMatrixParameterValues;

      /**
       * Create new instance
       *
       * @param url
       */
      public Repositories(String url)
      {
          _dsDispatcher = new DSDispatcher();
          _uriBuilder = new UriBuilder();
          List<String> _matrixParamSet;
          _matrixParamSet = _uriBuilder.addPathSegment(url);
          _matrixParamSet = _uriBuilder.addPathSegment("repositories");
          _templateAndMatrixParameterValues = new HashMap<>();
      }

      public DataSource getRepositoryDetailsListJson(String type)
          throws IOException {
          HashMap<String, Object> _queryParameterValues = new HashMap<>();
          HashMap<String, Object> _headerParameterValues = new HashMap<>();
          _queryParameterValues.put("type", type);
          String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
          DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.repositories.RepositoryDetailsList+json");
          return _retVal;
      }

      public static class RepoKeyConfiguration {

          private final DSDispatcher _dsDispatcher;
          private final UriBuilder _uriBuilder;
          private final HashMap<String, Object> _templateAndMatrixParameterValues;

          /**
           * Create new instance
           *
           */
          public RepoKeyConfiguration(final String url, String repokey)
          {
              _dsDispatcher = new DSDispatcher();
              _uriBuilder = new UriBuilder();
              List<String> _matrixParamSet;
              _matrixParamSet = _uriBuilder.addPathSegment(url);
              _matrixParamSet = _uriBuilder.addPathSegment("repositories");
              _matrixParamSet = _uriBuilder.addPathSegment("{repoKey}/configuration");
              _templateAndMatrixParameterValues = new HashMap<>();
              _templateAndMatrixParameterValues.put("repoKey", repokey);
          }

          /**
           * Get repoKey
           *
           */
          public String getRepoKey() {
              return ((String) _templateAndMatrixParameterValues.get("repoKey"));
          }

          /**
           * Set repoKey
           *
           */
          public void setRepoKey(String repokey) {
              _templateAndMatrixParameterValues.put("repoKey", repokey);
          }

          public DataSource getAsApplicationVndOrgJfrogArtifactoryRepositoriesRepositoryConfigurationJson()
              throws IOException {
              HashMap<String, Object> _queryParameterValues = new HashMap<>();
              HashMap<String, Object> _headerParameterValues = new HashMap<>();
              String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
              DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.repositories.RepositoryConfiguration+json");
              return _retVal;
          }

      }

  }
}

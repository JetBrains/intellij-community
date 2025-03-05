###
  @apiDefine SuccessExample

  @apiSuccessExample {json} Success-Response:
{
  "name": "WebStorm 140.SNAPSHOT",
  "productName": "WebStorm",
  "baselineVersion": 140
}
###

###
  @apiDefine SuccessExampleWithRegisteredFileTypes

  @apiSuccessExample {json} Success-Response (with registeredFileTypes):
{
  "name": "WebStorm 140.SNAPSHOT",
  "productName": "WebStorm",
  "baselineVersion": 140,
  "registeredFileTypes": [
    {
      "name": "HTML",
      "description": "HTML files",
      "isBinary": false
    },
    {
      "name": "XHTML",
      "description": "XHTML files",
      "isBinary": false
    },
    {
      "name": "DTD",
      "description": "XML Document Type Definition",
      "isBinary": false
    },
    {
      "name": "XML",
      "description": "XML files",
      "isBinary": false
    }
  ]
}
###